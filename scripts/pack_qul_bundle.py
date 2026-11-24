#!/usr/bin/env python3
"""Download the open QUL datasets and pack them into one optimized SQLite asset.

Provenance
----------
Everything textual comes from the Quranic Universal Library
(https://qul.tarteel.ai/resources).  QUL's per-resource download buttons are behind a
free account, but the same published datasets are served by QUL's public read API:

* word text, Uthmani/IndoPak scripts, word-by-word translation and transliteration:
  ``/api/v1/chapters/{surah}/verses?words=true&word_translation_language=<iso>``
* tafsir passages (Ibn Kathir, Jalalayn):
  ``/api/v1/tafsirs/{resource_id}/by_range?from=<surah>:1&to=<surah>:<ayahs>``
* surah metadata: ``/api/v1/chapters``

Morphology (root / lemma / stem / POS) is QUL resource "Quran morphology", whose
upstream is the Quranic Arabic Corpus; the corpus export is fetched from its public
mirror.  Ayah topics are scraped from QUL's public topic browser (``/topics``).

Downloads are cached in ``qul_raw/`` (git-ignored) so an interrupted run resumes where
it stopped.  Nothing copyrighted is bundled: only resources published as open data are
used, and each table records its provenance in ``qul_meta``.

Output: ``app/src/main/assets/qul_offline.db`` (gzipped to ``qul_offline.db.gzip`` when it
exceeds 25 MB; not ``.gz``, which the Android asset merge would silently unpack), then
prints ``QUL_DB_BYTES`` / ``QUL_DB_SHA256`` for the unpacked database so the constants
in ``QuranOfflinePack.kt`` can be pinned.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import html
import json
import os
import re
import sqlite3
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "qul_raw"
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
DB_PATH = ASSETS_DIR / "qul_offline.db"
# ``.gzip`` rather than ``.gz``: AGP's asset merge transparently gunzips ``*.gz``
# assets, which ships the raw 108 MB database under the name the app never looks for.
GZ_PATH = ASSETS_DIR / "qul_offline.db.gzip"

QUL_API = "https://qul.tarteel.ai/api/v1"
QUL_SITE = "https://qul.tarteel.ai"
QURAN_API = "https://api.quran.com/api/v4"
MORPHOLOGY_URL = (
    "https://raw.githubusercontent.com/mustafa0x/quran-morphology/master/quran-morphology.txt"
)

# QUL word-by-word translation resources that are open. `inh` is Ingush.
WBW_LANGUAGES = [
    ("en", "english"),
    ("ur", "urdu"),
    ("id", "indonesian"),
    ("tr", "turkish"),
    ("bn", "bengali"),
    ("hi", "hindi"),
    ("fr", "french"),
    ("ta", "tamil"),
    ("fa", "persian"),
    ("inh", "ingush"),
]

TAFSIRS = [
    ("ibn-kathir", 169, "Tafsir Ibn Kathir (abridged, English)"),
    ("jalalayn", 1620, "Tafsir Al-Jalalayn (English)"),
]

HEADERS = {
    "User-Agent": (
        "ImanCore-QUL-pack/1.0 (+https://github.com/farazsocialh-jpg/Sakinah_Deen) "
        "offline Quran bundler"
    ),
    "Accept": "application/json,text/html;q=0.9,*/*;q=0.8",
}

ARABIC_DIGITS = re.compile(r"^[\u0660-\u0669\u06F0-\u06F9]+$")
TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def log(message: str) -> None:
    print(message, flush=True)


def clean_text(value: str | None, keep_newlines: bool = False) -> str:
    if not value:
        return ""
    text = html.unescape(TAG_RE.sub(" ", value))
    if keep_newlines:
        text = re.sub(r"[ \t]+", " ", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        return text.strip()
    return WS_RE.sub(" ", text).strip()


def cache_path(name: str) -> Path:
    path = RAW_DIR / name
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def fetch_bytes(url: str, cache_name: str, *, refresh: bool = False, attempts: int = 4) -> bytes:
    """GET with an on-disk cache so a rerun never re-downloads the same resource."""
    path = cache_path(cache_name)
    if path.exists() and path.stat().st_size > 0 and not refresh:
        return path.read_bytes()
    last_error: Exception | None = None
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers=HEADERS)
            with urllib.request.urlopen(request, timeout=60) as response:
                payload = response.read()
            tmp = path.with_suffix(path.suffix + ".part")
            tmp.write_bytes(payload)
            tmp.replace(path)
            return payload
        except Exception as error:  # noqa: BLE001 - retried below
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"failed to fetch {url}: {last_error}")


def fetch_json(url: str, cache_name: str, *, refresh: bool = False):
    return json.loads(fetch_bytes(url, cache_name, refresh=refresh).decode("utf-8"))


def fetch_text(url: str, cache_name: str, *, refresh: bool = False) -> str:
    return fetch_bytes(url, cache_name, refresh=refresh).decode("utf-8", "replace")


def parallel(items, worker, label: str, workers: int = 6):
    """Run `worker` over `items`, keeping the output order and reporting progress."""
    results = [None] * len(items)
    done = 0
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(worker, item): index for index, item in enumerate(items)}
        for future in as_completed(futures):
            index = futures[future]
            results[index] = future.result()
            done += 1
            if done % 25 == 0 or done == len(items):
                log(f"  {label}: {done}/{len(items)}")
    return results


# --------------------------------------------------------------------------------------
# Chapters / metadata
# --------------------------------------------------------------------------------------


def load_chapters() -> list[dict]:
    data = fetch_json(f"{QUL_API}/chapters", "chapters.json")
    chapters = data.get("chapters") or []
    if len(chapters) != 114:
        raise SystemExit(f"expected 114 chapters from QUL, got {len(chapters)}")
    return chapters


# --------------------------------------------------------------------------------------
# Words: text, scripts, transliteration, word-by-word translations, page layout
# --------------------------------------------------------------------------------------


def word_cache_name(surah: int, lang: str) -> str:
    return f"words/words_{surah:03d}_{lang}.json"


def fetch_surah_words(surah: int, lang: str) -> dict:
    url = (
        f"{QUL_API}/chapters/{surah}/verses"
        "?words=true"
        "&word_fields=text_uthmani,text_indopak,en_transliteration"
        f"&word_translation_language={lang}"
        "&per_page=286"
    )
    return fetch_json(url, word_cache_name(surah, lang))


def build_words(chapters: list[dict], refresh: bool = False) -> None:
    """Fetch every surah for every WBW language; write one normalized JSON per surah."""
    log("Fetching word-by-word data from QUL ...")
    jobs = []
    for chapter in chapters:
        surah = int(chapter["id"])
        for lang, _ in WBW_LANGUAGES:
            path = cache_path(f"normalized/words_{surah:03d}.json")
            if path.exists() and not refresh:
                continue
            jobs.append((surah, lang))

    def worker(job):
        surah, lang = job
        try:
            return surah, lang, fetch_surah_words(surah, lang), None
        except Exception as error:  # noqa: BLE001 - surfaced after all downloads
            return surah, lang, None, error

    results = parallel(jobs, worker, "word pages", workers=8)
    failures = [(s, l, e) for s, l, _, e in results if e is not None]
    if failures:
        for surah, lang, error in failures[:5]:
            log(f"  ! surah {surah} {lang}: {error}")
        raise SystemExit(f"{len(failures)} word downloads failed; rerun to resume")

    by_surah: dict[int, dict[str, dict]] = {}
    for surah, lang, payload, _ in results:
        by_surah.setdefault(surah, {})[lang] = payload

    for surah in sorted(by_surah):
        languages = by_surah[surah]
        base = languages.get("en") or next(iter(languages.values()))
        verses = base.get("verses") or []
        rows = []
        translations: dict[str, dict[str, str]] = {lang: {} for lang in languages}
        # Index every language by verse key once: the API keeps chapter order stable,
        # but matching by key avoids both an O(n) lookup and any order assumption.
        words_by_lang = {
            lang: {
                str(verse.get("verse_key")): (verse.get("words") or [])
                for verse in (payload.get("verses") or [])
            }
            for lang, payload in languages.items()
        }
        for verse in verses:
            ayah_key = str(verse.get("verse_key") or "")
            if ":" not in ayah_key:
                continue
            for position, word in enumerate(verse.get("words") or [], start=1):
                text_uthmani = clean_text(word.get("text_uthmani"))
                text_indopak = clean_text(word.get("text_indopak"))
                raw_text = clean_text(word.get("text")) or text_uthmani
                if not raw_text or ARABIC_DIGITS.fullmatch(raw_text):
                    continue  # the ayah-number marker is not a word
                word_index = int(word.get("position") or position)
                word_key = f"{ayah_key}:{word_index}"
                rows.append(
                    {
                        "word_key": word_key,
                        "surah": int(verse.get("verse_key", "0:0").split(":")[0]),
                        "ayah": int(verse.get("verse_key", "0:0").split(":")[1]),
                        "word_index": word_index,
                        "uthmani": text_uthmani or raw_text,
                        "indopak": text_indopak or text_uthmani or raw_text,
                        "transliteration": clean_text(word.get("en_transliteration")),
                        "page_number": word.get("page_number") or verse.get("page_number"),
                        "line_number": word.get("line_number"),
                    }
                )
                for lang in languages:
                    lang_words = words_by_lang.get(lang, {}).get(ayah_key) or []
                    match = next(
                        (
                            candidate
                            for candidate in lang_words
                            if int(candidate.get("position") or 0) == word_index
                        ),
                        None,
                    )
                    translated = (match or {}).get("translation") or {}
                    value = clean_text(translated.get("text") if isinstance(translated, dict) else "")
                    if value and not re.fullmatch(r"\(\d+\)", value):
                        translations[lang][word_key] = value
        payload = {"surah": surah, "words": rows, "translations": translations}
        cache_path(f"normalized/words_{surah:03d}.json").write_text(
            json.dumps(payload, ensure_ascii=False), encoding="utf-8"
        )
        log(f"  normalized surah {surah:03d}: {len(rows)} words")


# --------------------------------------------------------------------------------------
# Morphology
# --------------------------------------------------------------------------------------


def build_morphology() -> None:
    log("Fetching Quranic Arabic Corpus morphology ...")
    text = fetch_text(MORPHOLOGY_URL, "quran-morphology.txt")
    aggregated: dict[str, dict[str, str]] = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) < 4:
            continue
        segments = parts[0].split(":")
        if len(segments) != 4:
            continue
        word_key = ":".join(segments[:3])
        tag = parts[2].strip()
        features = parts[3].split("|")
        entry = aggregated.setdefault(
            word_key, {"root": "", "lemma": "", "stem": "", "pos": "", "has_pos": ""}
        )
        for feature in features:
            if feature.startswith("ROOT:") and not entry["root"]:
                entry["root"] = feature[5:].replace(" ", "").strip()
            elif feature.startswith("LEM:") and not entry["lemma"]:
                entry["lemma"] = feature[4:].strip()
            elif feature.startswith("STEM:") and not entry["stem"]:
                entry["stem"] = feature[5:].strip()
        # Prefer the morphological head (noun/verb/particle) over a clitic segment.
        if tag and tag not in {"PREF", "SUFF"}:
            if not entry["has_pos"] or tag in {"N", "V", "PN", "ADJ", "P"} and entry["has_pos"] not in {"N", "V"}:
                entry["has_pos"] = tag
        if not entry["stem"]:
            entry["stem"] = parts[1].strip()
    rows = [
        {
            "word_key": key,
            "root": value["root"],
            "lemma": value["lemma"],
            "stem": value["stem"],
            "pos": value["has_pos"],
        }
        for key, value in aggregated.items()
        if value["root"] or value["lemma"] or value["has_pos"]
    ]
    cache_path("normalized/morphology.json").write_text(
        json.dumps(rows, ensure_ascii=False), encoding="utf-8"
    )
    log(f"  morphology words: {len(rows)}")


# --------------------------------------------------------------------------------------
# Tafsir
# --------------------------------------------------------------------------------------


def fetch_tafsir_chunk(slug: str, resource_id: int, surah: int, ayah_count: int) -> list[dict]:
    url = (
        f"{QUL_API}/tafsirs/{resource_id}/by_range"
        f"?from={surah}:1&to={surah}:{max(1, ayah_count)}"
    )
    data = fetch_json(url, f"tafsir/{slug}_{surah:03d}.json")
    rows = []
    for passage in data.get("tafsirs") or []:
        text = clean_text(passage.get("text"), keep_newlines=True)
        if not text:
            continue
        for ayah_key in passage.get("verses") or []:
            rows.append({"slug": slug, "ayah_key": str(ayah_key), "text": text})
    return rows


def build_tafsir(chapters: list[dict]) -> None:
    log("Fetching QUL tafsir passages ...")
    jobs = []
    for slug, resource_id, _ in TAFSIRS:
        for chapter in chapters:
            surah = int(chapter["id"])
            path = cache_path(f"tafsir/{slug}_{surah:03d}.json")
            if path.exists() and path.stat().st_size > 0:
                continue
            jobs.append((slug, resource_id, surah, int(chapter.get("verses_count") or 0)))

    def worker(job):
        try:
            return job, fetch_tafsir_chunk(*job), None
        except Exception as error:  # noqa: BLE001
            return job, None, error

    results = parallel(jobs, worker, "tafsir passages", workers=6)
    failures = [(job, error) for job, _, error in results if error is not None]
    if failures:
        for job, error in failures[:5]:
            log(f"  ! tafsir {job[0]} surah {job[2]}: {error}")
        raise SystemExit(f"{len(failures)} tafsir downloads failed; rerun to resume")

    rows = []
    for _, chunk, _ in results:
        rows.extend(chunk or [])
    cache_path("normalized/tafsir.json").write_text(
        json.dumps(rows, ensure_ascii=False), encoding="utf-8"
    )
    log(f"  tafsir rows: {len(rows)}")


# --------------------------------------------------------------------------------------
# Surah info (QUL chapter metadata + public surah introduction)
# --------------------------------------------------------------------------------------


def build_surah_info(chapters: list[dict]) -> None:
    log("Fetching surah info ...")
    rows = []

    def worker(chapter):
        surah = int(chapter["id"])
        try:
            data = fetch_json(
                f"{QURAN_API}/chapters/{surah}/info", f"surah_info/{surah:03d}.json"
            )
            info = data.get("chapter_info") or {}
            summary = clean_text(
                info.get("text") or info.get("short_text") or "", keep_newlines=True
            )
            return {
                "surah": surah,
                "lang": "en",
                "revelation_place": str(chapter.get("revelation_place") or ""),
                "summary": summary,
            }
        except Exception as error:  # noqa: BLE001 - metadata is best effort
            log(f"  ! surah info {surah}: {error}")
            return {
                "surah": surah,
                "lang": "en",
                "revelation_place": str(chapter.get("revelation_place") or ""),
                "summary": "",
            }

    rows = parallel(chapters, worker, "surah info", workers=6)
    cache_path("normalized/surah_info.json").write_text(
        json.dumps(rows, ensure_ascii=False), encoding="utf-8"
    )
    log(f"  surah info rows: {len(rows)}")


# --------------------------------------------------------------------------------------
# Ayah topics (QUL public topic browser)
# --------------------------------------------------------------------------------------

TOPIC_LINK_RE = re.compile(r'href="/topics/(\d+)"')
AYAH_KEY_RE = re.compile(r"\b\d{1,3}:\d{1,3}\b")


def build_ayah_topics() -> None:
    log("Scraping QUL ayah topics ...")
    topic_ids: set[int] = set()
    for page in range(1, 60):
        try:
            markup = fetch_text(f"{QUL_SITE}/topics?page={page}", f"topics_index/{page:02d}.html")
        except Exception as error:  # noqa: BLE001
            log(f"  ! topics index page {page}: {error}")
            break
        found = {int(value) for value in TOPIC_LINK_RE.findall(markup)}
        new = found - topic_ids
        topic_ids |= found
        if not new:
            break

    def worker(topic_id: int):
        try:
            markup = fetch_text(
                f"{QUL_SITE}/topics/{topic_id}", f"topics/{topic_id}.html"
            )
            title_match = re.search(r"<title>(.*?)</title>", markup, re.S)
            title = clean_text(title_match.group(1)) if title_match else f"Topic {topic_id}"
            keys = sorted(set(AYAH_KEY_RE.findall(markup)))
            return [
                {"ayah_key": key, "topic": title, "theme": None}
                for key in keys
                if key.count(":") == 1
            ]
        except Exception as error:  # noqa: BLE001
            log(f"  ! topic {topic_id}: {error}")
            return []

    rows = []
    for chunk in parallel(sorted(topic_ids), worker, "topics", workers=8):
        rows.extend(chunk)
    # Drop duplicates while keeping insertion order deterministic.
    seen = set()
    unique = []
    for row in rows:
        key = (row["ayah_key"], row["topic"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    cache_path("normalized/ayah_topics.json").write_text(
        json.dumps(unique, ensure_ascii=False), encoding="utf-8"
    )
    log(f"  ayah topic rows: {len(unique)}")


# --------------------------------------------------------------------------------------
# SQLite build
# --------------------------------------------------------------------------------------

SCHEMA = """
PRAGMA journal_mode = OFF;
PRAGMA synchronous = OFF;
PRAGMA temp_store = MEMORY;
PRAGMA page_size = 4096;

CREATE TABLE qul_words (
    id INTEGER PRIMARY KEY,
    word_key TEXT NOT NULL UNIQUE,
    surah INTEGER NOT NULL,
    ayah INTEGER NOT NULL,
    word_index INTEGER NOT NULL,
    uthmani TEXT NOT NULL,
    indopak TEXT NOT NULL,
    transliteration TEXT NOT NULL DEFAULT ''
);

CREATE TABLE qul_wbw_translations (
    word_key TEXT NOT NULL,
    lang TEXT NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (word_key, lang)
) WITHOUT ROWID;

CREATE TABLE qul_mushaf_pages (
    layout TEXT NOT NULL,
    page_number INTEGER NOT NULL,
    line_number INTEGER NOT NULL,
    line_type TEXT NOT NULL DEFAULT 'text',
    is_centered INTEGER NOT NULL DEFAULT 0,
    first_word_id INTEGER NOT NULL,
    last_word_id INTEGER NOT NULL,
    surah_number INTEGER NOT NULL,
    PRIMARY KEY (layout, page_number, line_number)
) WITHOUT ROWID;

CREATE TABLE qul_morphology (
    word_key TEXT PRIMARY KEY,
    root TEXT NOT NULL DEFAULT '',
    lemma TEXT NOT NULL DEFAULT '',
    stem TEXT NOT NULL DEFAULT '',
    pos TEXT NOT NULL DEFAULT ''
) WITHOUT ROWID;

CREATE TABLE qul_tafsir (
    slug TEXT NOT NULL,
    ayah_key TEXT NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (slug, ayah_key)
) WITHOUT ROWID;

CREATE TABLE qul_surah_info (
    surah INTEGER PRIMARY KEY,
    lang TEXT NOT NULL,
    revelation_place TEXT NOT NULL DEFAULT '',
    summary TEXT NOT NULL DEFAULT ''
);

CREATE TABLE qul_ayah_topics (
    ayah_key TEXT NOT NULL,
    topic TEXT NOT NULL,
    theme TEXT,
    PRIMARY KEY (ayah_key, topic)
) WITHOUT ROWID;

CREATE TABLE qul_meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
) WITHOUT ROWID;

CREATE INDEX idx_words_surah_ayah ON qul_words (surah, ayah, word_index);
CREATE INDEX idx_morph_root ON qul_morphology (root);
CREATE INDEX idx_wbw_lang ON qul_wbw_translations (lang, word_key);
CREATE INDEX idx_tafsir_ayah ON qul_tafsir (ayah_key, slug);
CREATE INDEX idx_topics_ayah ON qul_ayah_topics (ayah_key);
"""

LAYOUT = "madani"


def read_normalized(name: str):
    path = cache_path(f"normalized/{name}.json")
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def build_database(database_path: Path) -> dict:
    log("Building SQLite bundle ...")
    surahs = []
    for surah in range(1, 115):
        payload = read_normalized(f"words_{surah:03d}")
        if payload:
            surahs.append(payload)
    if len(surahs) != 114:
        raise SystemExit(f"only {len(surahs)}/114 surahs normalized; run the word stage first")

    if database_path.exists():
        database_path.unlink()
    connection = sqlite3.connect(database_path)
    try:
        connection.executescript(SCHEMA)
        word_rows = []
        wbw_rows = []
        page_rows: dict[tuple, dict] = {}
        next_id = 1
        for surah in surahs:
            for word in surah["words"]:
                word_id = next_id
                next_id += 1
                word_rows.append(
                    (
                        word_id,
                        word["word_key"],
                        int(word["surah"]),
                        int(word["ayah"]),
                        int(word["word_index"]),
                        word["uthmani"],
                        word["indopak"],
                        word.get("transliteration") or "",
                    )
                )
                page = word.get("page_number")
                line = word.get("line_number")
                if page and line:
                    slot = (LAYOUT, int(page), int(line))
                    entry = page_rows.get(slot)
                    if entry is None:
                        page_rows[slot] = {
                            "first": word_id,
                            "last": word_id,
                            "surah": int(word["surah"]),
                        }
                    else:
                        entry["last"] = word_id

            for lang, translations in surah.get("translations", {}).items():
                for word_key, text in translations.items():
                    if text:
                        wbw_rows.append((word_key, lang, text))

        connection.executemany(
            "INSERT INTO qul_words (id, word_key, surah, ayah, word_index, uthmani, indopak, transliteration) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            word_rows,
        )
        connection.executemany(
            "INSERT OR REPLACE INTO qul_wbw_translations (word_key, lang, text) VALUES (?, ?, ?)",
            wbw_rows,
        )
        connection.executemany(
            "INSERT INTO qul_mushaf_pages "
            "(layout, page_number, line_number, line_type, is_centered, first_word_id, last_word_id, surah_number) "
            "VALUES (?, ?, ?, 'text', 0, ?, ?, ?)",
            [
                (layout, page, line, entry["first"], entry["last"], entry["surah"])
                for (layout, page, line), entry in sorted(page_rows.items())
            ],
        )

        morphology = read_normalized("morphology") or []
        connection.executemany(
            "INSERT OR REPLACE INTO qul_morphology (word_key, root, lemma, stem, pos) VALUES (?, ?, ?, ?, ?)",
            [
                (row["word_key"], row["root"], row["lemma"], row["stem"], row["pos"])
                for row in morphology
            ],
        )
        tafsir = read_normalized("tafsir") or []
        connection.executemany(
            "INSERT OR REPLACE INTO qul_tafsir (slug, ayah_key, text) VALUES (?, ?, ?)",
            [(row["slug"], row["ayah_key"], row["text"]) for row in tafsir],
        )
        surah_info = read_normalized("surah_info") or []
        connection.executemany(
            "INSERT OR REPLACE INTO qul_surah_info (surah, lang, revelation_place, summary) VALUES (?, ?, ?, ?)",
            [
                (row["surah"], row["lang"], row["revelation_place"], row["summary"])
                for row in surah_info
            ],
        )
        topics = read_normalized("ayah_topics") or []
        connection.executemany(
            "INSERT OR REPLACE INTO qul_ayah_topics (ayah_key, topic, theme) VALUES (?, ?, ?)",
            [(row["ayah_key"], row["topic"], row["theme"]) for row in topics],
        )
        connection.executemany(
            "INSERT OR REPLACE INTO qul_meta (key, value) VALUES (?, ?)",
            [
                ("source", "Quranic Universal Library (https://qul.tarteel.ai/resources)"),
                ("word_languages", ",".join(lang for lang, _ in WBW_LANGUAGES)),
                ("tafsirs", ",".join(slug for slug, _, _ in TAFSIRS)),
                ("layout", LAYOUT),
                ("generated_at", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())),
            ],
        )
        connection.commit()
        log("Running ANALYZE / VACUUM ...")
        connection.execute("ANALYZE")
        connection.commit()
        connection.execute("VACUUM")
        counts = {
            table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            for table in (
                "qul_words",
                "qul_wbw_translations",
                "qul_mushaf_pages",
                "qul_morphology",
                "qul_tafsir",
                "qul_surah_info",
                "qul_ayah_topics",
            )
        }
        connection.close()
        return counts
    except Exception:
        connection.close()
        raise


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--stage",
        choices=["all", "words", "morphology", "tafsir", "surah-info", "topics", "build"],
        default="all",
    )
    parser.add_argument("--refresh", action="store_true", help="ignore the qul_raw cache")
    parser.add_argument(
        "--keep-db",
        action="store_true",
        help="keep an uncompressed .db even above the 25 MB threshold (debugging only)",
    )
    args = parser.parse_args()

    RAW_DIR.mkdir(parents=True, exist_ok=True)
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)

    chapters = load_chapters()
    if args.stage in ("all", "words"):
        build_words(chapters, refresh=args.refresh)
    if args.stage in ("all", "morphology"):
        build_morphology()
    if args.stage in ("all", "tafsir"):
        build_tafsir(chapters)
    if args.stage in ("all", "surah-info"):
        build_surah_info(chapters)
    if args.stage in ("all", "topics"):
        build_ayah_topics()

    if args.stage in ("all", "build") or not DB_PATH.exists():
        counts = build_database(DB_PATH)
        log("Row counts: " + ", ".join(f"{key}={value}" for key, value in counts.items()))

    raw_bytes = DB_PATH.stat().st_size
    digest = sha256_of(DB_PATH)
    if raw_bytes <= 25 * 1024 * 1024 or args.keep_db:
        if GZ_PATH.exists():
            GZ_PATH.unlink()
        log(f"kept uncompressed: {DB_PATH}")
    else:
        with DB_PATH.open("rb") as source, gzip.GzipFile(
            filename="qul_offline.db", mode="wb", fileobj=GZ_PATH.open("wb"), compresslevel=9, mtime=0
        ) as target:
            while True:
                chunk = source.read(1 << 20)
                if not chunk:
                    break
                target.write(chunk)
        log(f"gzipped: {GZ_PATH} ({GZ_PATH.stat().st_size:,} bytes)")
        # The packed asset is the gzip; keeping the 100 MB intermediate in the repo
        # would bloat both the working tree and every APK that packages assets/.
        DB_PATH.unlink()

    print(f"QUL_DB_BYTES={raw_bytes}")
    print(f"QUL_DB_SHA256={digest}")
    print(f"QUL_DB_GZ_BYTES={GZ_PATH.stat().st_size if GZ_PATH.exists() else 0}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
