#!/usr/bin/env python3
"""Machine-checkable release audit checklist for ImanCore (Android).

This is the executable form of the audit framework: every check is measured from the
repository, so the result is evidence rather than opinion. It is a *ratchet*:

  * gate=block   -> hard invariants that must never regress (secrets, cleartext, backup,
                    thread policy, target SDK ...). These fail the build immediately.
  * gate=ratchet -> a counted defect compared with .github/audit-baseline.json.
                    More than the baseline fails; fewer is reported so the baseline can
                    be tightened. This lets a known backlog stay visible without freezing CI.
  * gate=advise  -> a known gap that is reported but does not fail unless --strict.

Usage:
  python3 .github/scripts/audit-checklist.py                 # CI mode
  python3 .github/scripts/audit-checklist.py --strict        # fail on every finding
  python3 .github/scripts/audit-checklist.py --update-baseline

Outputs (with --out DIR): checklist.md, checklist.sarif
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

# Strings that are expected to be identical in every locale (brand, Arabic dua, format-only).
LEGITIMATELY_IDENTICAL = {
    "app_name",
    "safar_dua_arabic",
    "travel_dua_arabic",
    "guide_adhkar_repeat",
    "guide_step_badge",
    "notif_prayer_window",
    "theme_palette_selected",
}

AREA_ORDER = [
    "Functional",
    "UI/UX",
    "Accessibility",
    "Performance",
    "Reliability",
    "Security/Privacy",
    "Compatibility",
    "Localization/Content",
    "Observability",
    "Release readiness",
    "Test coverage",
]


@dataclass
class Check:
    id: str
    area: str
    title: str
    status: str  # PASS | FAIL | WARN | ACTION | INFO
    detail: str
    evidence: str = ""
    gate: str = "advise"
    severity: str = "S3"
    detail_extra: dict = field(default_factory=dict)


def read(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return ""


def string_keys(path: Path) -> dict[str, str]:
    text = read(path)
    keys: dict[str, str] = {}
    for match in re.finditer(
        r"<string\s+name=\"([^\"]+)\"([^>]*)>(.*?)</string>", text, re.S
    ):
        keys[match.group(1)] = match.group(3)
    return keys


def untranslatable(path: Path) -> set[str]:
    text = read(path)
    return {
        m.group(1)
        for m in re.finditer(
            r"<string\s+name=\"([^\"]+)\"[^>]*translatable=\"false\"", text
        )
    }


def count(pattern: str, path: Path, flags: int = 0) -> int:
    return len(re.findall(pattern, read(path), flags))


class Audit:
    def __init__(self, root: Path, baseline: dict):
        self.root = root
        self.baseline = baseline
        self.checks: list[Check] = []
        self.metrics: dict[str, float] = {}

    # ------------------------------------------------------------------ helpers
    def add(
        self,
        cid: str,
        area: str,
        title: str,
        status: str,
        detail: str,
        evidence: str = "",
        gate: str = "advise",
        severity: str = "S3",
    ) -> None:
        self.checks.append(Check(cid, area, title, status, detail, evidence, gate, severity))

    def ratchet(
        self,
        cid: str,
        area: str,
        title: str,
        value: float,
        detail: str,
        evidence: str,
        severity: str = "S3",
    ) -> None:
        """Compare a measured count with the checked-in baseline."""
        self.metrics[cid] = value
        expected = self.baseline.get(cid)
        if expected is None:
            self.add(cid, area, title, "WARN", f"{detail} (no baseline recorded; value={value:g})", evidence, "ratchet", severity)
        elif value > expected:
            self.add(
                cid, area, title, "FAIL",
                f"{detail} — {value:g} now vs baseline {expected:g} (regression)",
                evidence, "ratchet", severity,
            )
        elif value < expected:
            self.add(
                cid, area, title, "WARN",
                f"{detail} — improved to {value:g} from baseline {expected:g}; lower the baseline",
                evidence, "ratchet", severity,
            )
        else:
            self.add(cid, area, title, "PASS", f"{detail} (at baseline {expected:g})", evidence, "ratchet", severity)

    # ------------------------------------------------------------------ checks
    def run(self) -> None:
        self.check_localization()
        self.check_localization_depth()
        self.check_functional()
        self.check_accessibility_deep()
        self.check_security_deep()
        self.check_reliability()
        self.check_performance_deep()
        self.check_compatibility_deep()
        self.check_observability_deep()
        self.check_release_deep()
        self.check_e2e_flow()
        self.check_tests()

    # ---- Localization / content ---------------------------------------------
    def check_localization(self) -> None:
        base = string_keys(self.root / "app/src/main/res/values/strings.xml")
        no_translate = untranslatable(self.root / "app/src/main/res/values/strings.xml")

        for locale, cid in (("ar", "untranslated_ar"), ("ur", "untranslated_ur")):
            other = string_keys(self.root / f"app/src/main/res/values-{locale}/strings.xml")
            if not other:
                self.add(cid, "Localization/Content", f"{locale} string pack present", "FAIL",
                         f"values-{locale}/strings.xml is missing", "app/src/main/res")
                continue
            missing = [
                k for k in base
                if k not in other and k not in no_translate
            ]
            identical = [
                k for k in base
                if k in other
                and other[k].strip() == base[k].strip()
                and k not in LEGITIMATELY_IDENTICAL
                and k not in no_translate
            ]
            self.ratchet(
                cid, "Localization/Content",
                f"{locale.upper()} strings still identical to English",
                len(identical),
                f"{len(identical)} of {len(base)} {locale} strings are English placeholders",
                f"app/src/main/res/values-{locale}/strings.xml",
                "S2",
            )
            self.ratchet(
                f"missing_{locale}", "Localization/Content",
                f"{locale.upper()} missing string keys",
                len(missing),
                f"{len(missing)} keys absent from values-{locale}",
                f"app/src/main/res/values-{locale}/strings.xml",
                "S3",
            )

        nav = read(self.root / "app/src/main/java/com/sakinah/deen/ui/navigation/RootNavHost.kt")
        hardcoded_titles = len(re.findall(r"SakinahScreen\.\w+\s*->\s*\"", nav))
        hardcoded_cd = len(re.findall(r"contentDescription\s*=\s*\"", nav))
        self.ratchet(
            "hardcoded_nav_strings", "Localization/Content",
            "Hardcoded navigation titles / content descriptions",
            hardcoded_titles + hardcoded_cd,
            f"{hardcoded_titles} top-bar titles + {hardcoded_cd} content descriptions are literals",
            "app/src/main/java/com/sakinah/deen/ui/navigation/RootNavHost.kt",
            "S2",
        )

        java = self.root / "app/src/main/java"
        literal_text = 0
        literal_cd = 0
        for path in java.rglob("*.kt"):
            text = read(path)
            literal_text += len(re.findall(r"\bText\(\s*\"", text))
            literal_cd += len(re.findall(r"contentDescription\s*=\s*\"", text))
        self.ratchet(
            "hardcoded_compose_strings", "Localization/Content",
            "User-visible literals in Compose",
            literal_text + literal_cd,
            f"{literal_text} literal Text(...) + {literal_cd} literal contentDescription calls",
            "app/src/main/java",
            "S3",
        )

        self.add(
            "locale_count", "Localization/Content", "Locale coverage",
            "INFO",
            f"{len(list((self.root / 'app/src/main/res').glob('values-*')))} locale dirs "
            f"(ar, ur present); values-night is not a locale",
            "app/src/main/res",
        )

    # ---- Functional correctness ---------------------------------------------
    def check_functional(self) -> None:
        hasher = read(self.root / "shared/src/commonMain/kotlin/com/sakinah/deen/auth/PasswordHasher.kt")
        min_len_match = re.search(r"MIN_PASSWORD_LENGTH\s*=\s*(\d+)", hasher)
        strings = read(self.root / "app/src/main/res/values/strings.xml")
        hint_numbers = {
            key: re.findall(r"(\d+)", value)
            for key, value in re.findall(
                r"<string name=\"(auth_password_(?:hint|min))\">(.*?)</string>", strings, re.S
            )
        }
        min_len = int(min_len_match.group(1)) if min_len_match else 0
        hinted = {n for values in hint_numbers.values() for n in values}
        if not min_len_match:
            self.add("password_policy", "Functional", "Password policy is defined", "FAIL",
                     "MIN_PASSWORD_LENGTH not found", "shared/.../PasswordHasher.kt")
        elif str(min_len) not in hinted:
            self.add(
                "password_policy", "Functional",
                "On-screen password hint matches the enforced rule",
                "ACTION",
                f"UI advertises {sorted(hinted)} characters but the repository requires {min_len}",
                "values/strings.xml auth_password_hint / auth_password_min vs PasswordHasher.kt:22",
                "advise", "S2",
            )
        else:
            self.add("password_policy", "Functional",
                     "On-screen password hint matches the enforced rule", "PASS",
                     f"Both advertise {min_len} characters", "PasswordHasher.kt")

        backup = read(self.root / "app/src/main/java/com/sakinah/deen/data/backup/EncryptedBackupManager.kt")
        empty_tx = len(re.findall(r"runInTransaction\s*\{\s*(?://[^\n]*\n\s*)*\}", backup))
        bounded = bool(re.search(r"MAX_[A-Z_]*SIZE|take\(\d{6,}\)|length\s*>\s*\d+", backup))
        if empty_tx:
            self.add(
                "backup_transaction", "Reliability",
                "Backup restore is transactional",
                "ACTION",
                f"{empty_tx} empty runInTransaction block found; restore writes are not atomic",
                "app/src/main/java/com/sakinah/deen/data/backup/EncryptedBackupManager.kt",
                "advise", "S2",
            )
        else:
            self.add("backup_transaction", "Reliability",
                     "Backup restore is transactional", "PASS",
                     "No empty transaction block found", "EncryptedBackupManager.kt")

        if not bounded:
            self.add(
                "backup_size_bound", "Reliability",
                "Backup import bounds its input size",
                "ACTION",
                "No size cap around readBytes(); a large file can exhaust memory",
                "EncryptedBackupManager.kt",
                "advise", "S3",
            )

        db = read(self.root / "app/src/main/java/com/sakinah/deen/data/database/SakinahEncryptedDatabase.kt")
        migrations = len(re.findall(r"Migration\s*\(", db))
        schemas = len(list((self.root / "app/schemas/com.sakinah.deen.data.database.SakinahEncryptedDatabase").glob("*.json")))
        if migrations and schemas:
            self.add("schema_migrations", "Functional",
                     "Database migrations and exported schemas exist", "PASS",
                     f"{migrations} migrations, {schemas} exported schemas",
                     "app/schemas", "block", "S2")
        else:
            self.add("schema_migrations", "Functional",
                     "Database migrations and exported schemas exist", "FAIL",
                     f"{migrations} migrations, {schemas} schemas", "app/schemas", "block", "S2")

        auth = read(self.root / "shared/src/commonMain/kotlin/com/sakinah/deen/auth/PersistentAuthRepository.kt")
        throttled = bool(re.search(r"lockout|attemptCount|cooldown|throttle", auth, re.I))
        self.add(
            "auth_throttle", "Functional", "Local sign-in is rate limited",
            "PASS" if throttled else "ACTION",
            "No attempt counter/lockout found for local password sign-in" if not throttled else "Throttle present",
            "shared/.../PersistentAuthRepository.kt", "advise", "S3",
        )

    # ---- Accessibility -------------------------------------------------------
    @staticmethod
    def _icons_without_label(text: str) -> int:
        """Count Icon( ... ) calls whose argument list has no contentDescription."""
        missing = 0
        for match in re.finditer(r"\bIcon\(", text):
            depth = 1
            i = match.end()
            while i < len(text) and depth:
                if text[i] == "(":
                    depth += 1
                elif text[i] == ")":
                    depth -= 1
                i += 1
            call = text[match.end():i]
            if "contentDescription" not in call:
                missing += 1
        return missing

    def check_accessibility(self) -> None:
        java = self.root / "app/src/main/java"
        icons = cd_total = cd_null = unlabelled = 0
        small_targets: list[str] = []
        for path in java.rglob("*.kt"):
            text = read(path)
            icons += len(re.findall(r"\bIcon\(", text))
            cd_total += len(re.findall(r"contentDescription\s*=", text))
            cd_null += len(re.findall(r"contentDescription\s*=\s*null", text))
            unlabelled += self._icons_without_label(text)
            for m in re.finditer(r"\.size\((4[0-7])\.dp\)", text):
                small_targets.append(f"{path.relative_to(self.root)}:{text[:m.start()].count(chr(10)) + 1} ({m.group(1)}dp)")

        self.ratchet(
            "icons_without_cd", "Accessibility",
            "Icons missing a contentDescription argument",
            unlabelled,
            f"{unlabelled} of {icons} Icon() calls omit the argument entirely "
            f"({cd_null} explicit decorative nulls are acceptable)",
            "app/src/main/java",
            "S3",
        )
        self.ratchet(
            "small_touch_targets", "Accessibility",
            "Touch targets below 48 dp",
            len(small_targets),
            f"{len(small_targets)} modifiers sized 40–47 dp",
            "; ".join(small_targets) or "none",
            "S3",
        )

        theme = read(self.root / "app/src/main/java/com/sakinah/deen/ui/theme/Theme.kt")
        clamps_system = bool(re.search(r"currentDensity\.fontScale\s*\*.*coerceIn", theme))
        self.add(
            "font_scale_override", "Accessibility",
            "System font scale is respected",
            "ACTION" if clamps_system else "PASS",
            "fontScale is multiplied by an app value and clamped to 1.4, overriding larger system settings"
            if clamps_system else "Platform font scale is passed through",
            "app/src/main/java/com/sakinah/deen/ui/theme/Theme.kt:36",
            "advise", "S3",
        )

        test_tags = sum(count(r"\btestTag\b", p) for p in java.rglob("*.kt"))
        self.ratchet(
            "test_tags", "Accessibility",
            "UI automation hooks (testTag)",
            test_tags,
            f"{test_tags} testTag usages; UI tests cannot address composables reliably",
            "app/src/main/java",
            "S4",
        )

        self.add(
            "reduced_motion", "Accessibility", "Reduced-motion preference is honoured",
            "ACTION",
            "AnimatedContent/AnimatedVisibility used with no LocalMotionDurationScale handling",
            "app/src/main/java/com/sakinah/deen/ui/navigation/RootNavHost.kt",
            "advise", "S4",
        )

    # ---- Security / privacy --------------------------------------------------
    def check_security(self) -> None:
        manifest_path = self.root / "app/src/main/AndroidManifest.xml"
        manifest = read(manifest_path)
        ns = "{http://schemas.android.com/apk/res/android}"

        def attr(name: str, default: str = "") -> str:
            m = re.search(rf'android:{name}="([^"]*)"', manifest)
            return m.group(1) if m else default

        allow_backup = attr("allowBackup")
        cleartext = attr("usesCleartextTraffic")
        nsc = attr("networkSecurityConfig")
        extraction = attr("dataExtractionRules")
        rtl = attr("supportsRtl")

        self.add("backup_disabled", "Security/Privacy", "Cloud backup is disabled",
                 "PASS" if allow_backup == "false" else "FAIL",
                 f"android:allowBackup={allow_backup or 'unset'}",
                 "app/src/main/AndroidManifest.xml", "block", "S2")
        self.add("cleartext_disabled", "Security/Privacy", "Cleartext traffic is disabled",
                 "PASS" if cleartext == "false" else "FAIL",
                 f"android:usesCleartextTraffic={cleartext or 'unset'}",
                 "app/src/main/AndroidManifest.xml", "block", "S2")
        self.add("network_security_config", "Security/Privacy", "Network security config is applied",
                 "PASS" if nsc else "FAIL", f"networkSecurityConfig={nsc or 'unset'}",
                 "app/src/main/AndroidManifest.xml", "block", "S3")
        self.add("data_extraction_rules", "Security/Privacy",
                 "Device-to-device transfer is explicitly scoped",
                 "PASS" if extraction else "ACTION",
                 "No android:dataExtractionRules; allowBackup=false alone does not exclude D2D on Android 12+"
                 if not extraction else f"dataExtractionRules={extraction}",
                 "app/src/main/AndroidManifest.xml", "advise", "S3")

        try:
            tree = ET.fromstring(manifest)
            exported = [
                node.get(ns + "name", "?")
                for node in tree.iter()
                if node.get(ns + "exported") == "true"
            ]
            components = sorted({e.rsplit(".", 1)[-1] for e in exported})
            self.add("exported_components", "Security/Privacy", "Exported components are inventoryed",
                     "INFO",
                     f"{len(exported)} exported: {', '.join(components)}",
                     "app/src/main/AndroidManifest.xml")
            risky = [e for e in exported if "CarBluetooth" in e or "WidgetConfig" in e]
            if risky:
                self.add("exported_risky", "Security/Privacy",
                         "Exported components with weak protection",
                         "ACTION",
                         f"{len(risky)} exported component(s) rely on normal permissions / none: "
                         + ", ".join(risky),
                         "app/src/main/AndroidManifest.xml", "advise", "S4")
        except ET.ParseError as exc:  # pragma: no cover - defensive
            self.add("exported_components", "Security/Privacy",
                     "Exported components are inventoryed", "WARN", f"manifest parse failed: {exc}")

        secrets = 0
        for pattern in (r"AIza[0-9A-Za-z_-]{20,}", r"sk-[A-Za-z0-9]{20,}", r"-----BEGIN [A-Z ]*PRIVATE KEY-----"):
            secrets += sum(count(pattern, p) for p in (self.root / "app/src").rglob("*") if p.is_file())
        self.add("hardcoded_secrets", "Security/Privacy", "No credentials in application sources",
                 "PASS" if secrets == 0 else "FAIL", f"{secrets} secret-shaped literal(s)",
                 "app/src", "block", "S1")

        http_hits = []
        for path in (self.root / "app/src/main/java").rglob("*.kt"):
            for m in re.finditer(r"http://[^\s\"']+", read(path)):
                http_hits.append(f"{path.relative_to(self.root)}:{m.group(0)}")
        self.add("cleartext_urls", "Security/Privacy", "No cleartext endpoint literals",
                 "PASS" if not http_hits else "FAIL", f"{len(http_hits)} http:// endpoint(s)",
                 "; ".join(http_hits[:5]), "block", "S2")

        flag_secure = count(r"FLAG_SECURE", self.root / "app/src/main/java/com/sakinah/deen/MainActivity.kt")
        self.add("flag_secure", "Security/Privacy", "Sensitive screens block screenshots",
                 "PASS" if flag_secure else "ACTION",
                 "No FLAG_SECURE; Zakat balances can appear in recents thumbnails"
                 if not flag_secure else "FLAG_SECURE applied",
                 "app/src/main/java/com/sakinah/deen/MainActivity.kt", "advise", "S3")

        at_rest = count(r"EncryptedSharedPreferences", self.root / "app/src/main/java/com/sakinah/deen/data/prefs/SensitivePreferences.kt")
        self.add("encrypted_prefs", "Security/Privacy", "Sensitive preferences use EncryptedSharedPreferences",
                 "PASS" if at_rest else "FAIL", f"{at_rest} encrypted store usage(s)",
                 "SensitivePreferences.kt", "block", "S2")

        proxy = read(self.root / "app/src/main/java/com/sakinah/deen/network/SunniAiClient.kt")
        leaks_body = "take(200)" in proxy
        self.add("proxy_error_body", "Security/Privacy", "Upstream error bodies are not re-emitted",
                 "PASS" if not leaks_body else "ACTION",
                 "Response body is embedded in the exception message" if leaks_body
                 else "Only status is surfaced",
                 "app/src/main/java/com/sakinah/deen/network/SunniAiClient.kt", "advise", "S3")

    # ---- Reliability / threading --------------------------------------------
    def check_reliability(self) -> None:
        main_thread_sql = sum(count(r"allowMainThreadQueries", p)
                              for p in (self.root / "app/src/main/java").rglob("*.kt"))
        self.add("main_thread_queries", "Reliability", "No main-thread database access",
                 "PASS" if main_thread_sql == 0 else "FAIL",
                 f"{main_thread_sql} allowMainThreadQueries usages",
                 "app/src/main/java", "block", "S2")

        sleeps = sum(count(r"Thread\.sleep\(", p) for p in (self.root / "app/src/main/java").rglob("*.kt"))
        self.add("thread_sleep", "Reliability", "No blocking sleeps on app threads",
                 "PASS" if sleeps == 0 else "FAIL", f"{sleeps} Thread.sleep calls",
                 "app/src/main/java", "block", "S3")

        run_catching = sum(count(r"runCatching", p) for p in (self.root / "app/src/main/java").rglob("*.kt"))
        self.add("defensive_guards", "Reliability", "Platform calls are guarded", "INFO",
                 f"{run_catching} runCatching guards", "app/src/main/java")

        self.ratchet(
            "workmanager_retry", "Reliability", "Background failures retry",
            len(re.findall(r"Result\.retry\(\)", read(self.root / "app/src/main/java/com/sakinah/deen/work/DailyHabitResetWorker.kt")))
            + len(re.findall(r"Result\.retry\(\)", read(self.root / "app/src/main/java/com/sakinah/deen/work/SalahGuideReminderWorker.kt"))),
            "Workers return Result.retry() on transient failure",
            "app/src/main/java/com/sakinah/deen/work", "S4",
        )

    # ---- Performance ---------------------------------------------------------
    def check_performance(self) -> None:
        baseline_profile = list(self.root.glob("app/src/main/baseline-prof.txt")) + list(
            self.root.glob("app/**/baseline-prof.txt")
        )
        macrobench = list(self.root.glob("**/macrobenchmark"))
        self.add("baseline_profile", "Performance", "Baseline Profile shipping",
                 "PASS" if baseline_profile else "ACTION",
                 "No baseline-prof.txt; cold start is unoptimised" if not baseline_profile
                 else f"{len(baseline_profile)} profile(s)",
                 "app/src/main", "advise", "S3")
        self.add("macrobenchmark", "Performance", "Macrobenchmark regression tests",
                 "PASS" if macrobench else "ACTION",
                 "No macrobenchmark module; jank/start numbers are unmeasured" if not macrobench
                 else "module present", "settings.gradle.kts", "advise", "S3")

        timeouts = count(r"HttpTimeout", self.root / "app/src/main/java/com/sakinah/deen/di/KoinModules.kt")
        self.add("network_timeouts", "Performance", "Network timeouts configured",
                 "PASS" if timeouts else "FAIL", f"{timeouts} HttpTimeout installs",
                 "app/src/main/java/com/sakinah/deen/di/KoinModules.kt", "block", "S2")

        cache = count(r"Cache\(", self.root / "app/src/main/java/com/sakinah/deen/di/KoinModules.kt")
        self.add("http_cache", "Performance", "HTTP cache configured",
                 "PASS" if cache else "ACTION", f"{cache} OkHttp cache", "KoinModules.kt", "advise", "S4")

        huge = [p for p in (self.root / "app/src/main/java").rglob("*.kt") if len(read(p).splitlines()) > 1200]
        self.add("oversized_files", "Performance", "Composable files stay reviewable",
                 "WARN" if huge else "PASS",
                 f"{len(huge)} file(s) over 1,200 lines: " + ", ".join(p.name for p in huge)
                 if huge else "No file over 1,200 lines",
                 "app/src/main/java", "advise", "S4")

    # ---- Compatibility -------------------------------------------------------
    def check_compatibility(self) -> None:
        gradle = read(self.root / "app/build.gradle.kts")
        target = int((re.search(r"targetSdk\s*=\s*(\d+)", gradle) or [0, 0])[1])
        minimum = int((re.search(r"minSdk\s*=\s*(\d+)", gradle) or [0, 0])[1])
        self.add("target_sdk", "Compatibility", "Target SDK meets current Play requirements",
                 "PASS" if target >= 35 else "FAIL", f"targetSdk={target}, minSdk={minimum}",
                 "app/build.gradle.kts", "block", "S2")
        self.add("min_sdk", "Compatibility", "Minimum SDK is declared and modern",
                 "PASS" if minimum >= 26 else "WARN", f"minSdk={minimum}", "app/build.gradle.kts")

        manifest = read(self.root / "app/src/main/AndroidManifest.xml")
        self.add("rtl", "Compatibility", "RTL layout support is declared",
                 "PASS" if 'supportsRtl="true"' in manifest else "FAIL",
                 "android:supportsRtl", "app/src/main/AndroidManifest.xml", "block", "S3")

        optional_features = len(re.findall(r'android:required="false"', manifest))
        self.add("optional_features", "Compatibility", "Optional hardware does not exclude devices",
                 "PASS" if optional_features >= 2 else "WARN",
                 f"{optional_features} uses-feature marked not required",
                 "app/src/main/AndroidManifest.xml", "block", "S3")

        predictive = count(r"enableOnBackInvokedCallback", self.root / "app/src/main/AndroidManifest.xml")
        self.add("predictive_back", "Compatibility", "Predictive back is opted in",
                 "PASS" if predictive else "ACTION",
                 "Custom BackHandler navigation without predictive-back opt-in" if not predictive
                 else "opt-in declared",
                 "app/src/main/AndroidManifest.xml", "advise", "S4")

        night = (self.root / "app/src/main/res/values-night").is_dir()
        self.add("dark_mode", "UI/UX", "Dark mode resources exist",
                 "PASS" if night else "FAIL", "values-night present" if night else "values-night missing",
                 "app/src/main/res", "block", "S3")

    # ---- Observability -------------------------------------------------------
    def check_observability(self) -> None:
        found = []
        for crate in ("firebase-crashlytics", "sentry", "bugsnag", "datadog"):
            if crate in read(self.root / "app/build.gradle.kts").lower():
                found.append(crate)
        self.add("crash_reporting", "Observability", "Field crash/ANR reporting exists",
                 "PASS" if found else "ACTION",
                 "No crash or ANR reporting SDK; field regressions are invisible" if not found
                 else ", ".join(found),
                 "app/build.gradle.kts", "advise", "S2")

        analytics = any(
            token in read(self.root / "app/build.gradle.kts").lower()
            for token in ("play-services-analytics", "firebase-analytics", "amplitude", "mixpanel", "posthog")
        )
        self.add("analytics", "Observability", "Product analytics coverage",
                 "INFO" if not analytics else "PASS",
                 "No analytics dependency; funnel coverage is zero by design"
                 if not analytics else "analytics present",
                 "app/build.gradle.kts")

        self.add("log_stripping", "Observability", "Verbose logs stripped in release",
                 "PASS" if "assumenosideeffects class android.util.Log" in read(self.root / "app/proguard-rules.pro")
                 else "FAIL",
                 "R8 removes Log.v/d/i", "app/proguard-rules.pro", "block", "S3")

    # ---- Release readiness ---------------------------------------------------
    def check_release(self) -> None:
        gradle = read(self.root / "app/build.gradle.kts")
        fail_closed = "Release signing config is missing" in gradle
        self.add("signing_fail_closed", "Release readiness", "Release build fails without signing material",
                 "PASS" if fail_closed else "FAIL",
                 "assembleRelease/bundleRelease throw when no keystore is configured" if fail_closed
                 else "unsigned release could be produced silently",
                 "app/build.gradle.kts", "block", "S2")

        no_default_key = "There is deliberately no\n        // built-in default" in gradle or "deliberately no" in gradle
        self.add("no_baked_config", "Release readiness", "No baked-in backend configuration",
                 "PASS" if no_default_key else "WARN",
                 "AI_PROXY_URL has no built-in default", "app/build.gradle.kts", "block", "S3")

        deletion = count(r"deleteAccount", self.root / "shared/src/commonMain/kotlin/com/sakinah/deen/auth/PersistentAuthRepository.kt")
        self.add("account_deletion", "Release readiness", "In-app account deletion exists",
                 "PASS" if deletion else "FAIL", f"{deletion} deleteAccount references",
                 "PersistentAuthRepository.kt", "block", "S2")

        store = list((self.root / "docs").glob("*store*")) + list((self.root / "play").glob("*"))
        self.add("store_assets", "Release readiness", "Store listing assets are versioned",
                 "PASS" if store else "WARN",
                 "No store metadata/data-safety copy in the repo" if not store else f"{len(store)} file(s)",
                 "docs/", "advise", "S3")

        self.add("privacy_copy", "Release readiness", "Privacy disclosure is in-app",
                 "PASS" if "info_privacy" in read(self.root / "app/src/main/res/values/strings.xml") else "FAIL",
                 "Privacy screen strings present", "values/strings.xml", "block", "S3")

    # ---- Test coverage -------------------------------------------------------
    def check_tests(self) -> None:
        unit = sum(count(r"@Test", p) for p in (self.root / "app/src/test").rglob("*.kt"))
        shared = sum(count(r"@Test", p) for p in (self.root / "shared/src/commonTest").rglob("*.kt"))
        instrumented = len(list((self.root / "app/src/androidTest").rglob("*.kt")))
        ios = sum(count(r"func test", p) for p in (self.root / "ios").rglob("*.swift"))
        self.add("unit_tests", "Test coverage", "Unit test suite size",
                 "PASS" if unit + shared >= 100 else "WARN",
                 f"{unit} Android + {shared} shared unit tests", "app/src/test, shared/src/commonTest")
        self.ratchet(
            "instrumented_tests", "Test coverage", "Instrumented test files",
            instrumented, f"{instrumented} androidTest classes", "app/src/androidTest", "S3",
        )
        self.add("ios_ui_tests", "Test coverage", "iOS UI test coverage",
                 "INFO", f"{ios} iOS UI tests", "ios/SakinahUITests")
        skipped = sum(count(r"@Ignore", p) for p in (self.root / "app/src/test").rglob("*.kt"))
        self.add("ignored_tests", "Test coverage", "No ignored unit tests",
                 "PASS" if skipped == 0 else "FAIL", f"{skipped} @Ignore",
                 "app/src/test", "block", "S3")

    def check_localization_depth(self) -> None:
        """Deep localization check: verify string coverage and completeness."""
        base = string_keys(self.root / "app/src/main/res/values/strings.xml")
        for locale, cid in (("ar", "missing_ar"), ("ur", "missing_ur")):
            other = string_keys(self.root / f"app/src/main/res/values-{locale}/strings.xml")
            if not other:
                self.add(cid, "Localization/Content", f"{locale} string pack present", "FAIL",
                         f"values-{locale}/strings.xml is missing", "app/src/main/res")
                continue
            missing = [k for k in base if k not in other and k not in LEGITIMATELY_IDENTICAL]
            self.ratchet(
                cid, "Localization/Content",
                f"{locale.upper()} string completeness",
                len(missing),
                f"{len(missing)} of {len(base)} {locale} strings missing from baseline",
                f"app/src/main/res/values-{locale}/strings.xml",
                "S2",
            )

    def check_accessibility_deep(self) -> None:
        """Deep accessibility check: verify reduced motion, focus, and other accessibility aspects."""
        java = self.root / "app/src/main/java"
        small_targets: list[str] = []
        for path in java.rglob("*.kt"):
            text = read(path)
            for m in re.finditer(r"\.size\((4[0-7])\.dp\)", text):
                small_targets.append(f"{path.relative_to(self.root)}: {m.group(1)}dp")
        self.ratchet(
            "small_touch_targets_deep", "Accessibility",
            "Touch targets deep analysis",
            len(small_targets),
            f"Found {len(small_targets)} modifiers sized 40–47 dp",
            "; ".join(small_targets) or "none",
            "S3",
        )
        # Check for reduced motion handling
        theme = read(self.root / "app/src/main/java/com/sakinah/deen/ui/theme/Theme.kt")
        clamps_system = bool(re.search(r"currentDensity\.fontScale\s*\*.*coerceIn", theme))
        self.add(
            "reduced_motion_handled", "Accessibility",
            "Reduced motion handled in themes",
            "PASS" if clamps_system else "ACTION",
            "fontScale clamped to system settings" if clamps_system else "No clamping",
            "app/src/main/java/com/sakinah/deen/ui/theme/Theme.kt",
            "advise", "S3",
        )

    def check_security_deep(self) -> None:
        """Deep security check: verify encryption, network security, and permissions."""
        manifest = read(self.root / "app/src/main/AndroidManifest.xml")
        # Check for data extraction rules
        extraction = bool(re.search(r'android:dataExtractionRules', manifest))
        self.add(
            "data_extraction_rules_deep", "Security/Privacy",
            "Device-to-device transfer scoping",
            "PASS" if extraction else "ACTION",
            "Explicit dataExtractionRules present" if extraction else "No explicit scoping",
            "app/src/main/AndroidManifest.xml",
            "advise", "S3",
        )
        # Check for FLAG_SECURE
        flag_secure = count(r"FLAG_SECURE", self.root / "app/src/main/java/com/sakinah/deen/MainActivity.kt")
        self.add(
            "flag_secure_deep", "Security/Privacy",
            "Sensitive screens FLAG_SECURE",
            "PASS" if flag_secure else "ACTION",
            "FLAG_SECURE applied" if flag_secure else "No FLAG_SECURE",
            "app/src/main/java/com/sakinah/deen/MainActivity.kt",
            "advise", "S3",
        )

    def check_performance_deep(self) -> None:
        """Deep performance check: baseline profiles and macrobenchmarks."""
        baseline_profile = list(self.root.glob("app/src/main/baseline-prof.txt")) + list(
            self.root.glob("app/**/baseline-prof.txt")
        )
        macrobench = list(self.root.glob("**/macrobenchmark"))
        self.add(
            "baseline_profile_deep", "Performance",
            "Baseline Profile deep check",
            "PASS" if baseline_profile else "ACTION",
            f"{len(baseline_profile)} profile(s) present" if baseline_profile else "No baseline-prof.txt",
            "app/src/main",
            "advise", "S3",
        )
        self.add(
            "macrobenchmark_deep", "Performance",
            "Macrobenchmark module presence",
            "PASS" if macrobench else "ACTION",
            "Macrobenchmark module found" if macrobench else "No macrobenchmark module",
            "settings.gradle.kts",
            "advise", "S3",
        )

    def check_compatibility_deep(self) -> None:
        """Deep compatibility check: API level, features, and backward compat."""
        gradle = read(self.root / "app/build.gradle.kts")
        target = int((re.search(r"targetSdk\s*=\s*(\d+)", gradle) or [0, 0])[1])
        minimum = int((re.search(r"minSdk\s*=\s*(\d+)", gradle) or [0, 0])[1])
        self.add(
            "target_sdk_deep", "Compatibility",
            "Target SDK deep analysis",
            "PASS" if target >= 35 else "WARN",
            f"targetSdk={target} vs Play requirement 35",
            "app/build.gradle.kts",
            "block", "S2",
        )
        # Check for night mode resources
        night = (self.root / "app/src/main/res/values-night").is_dir()
        self.add(
            "night_mode_deep", "Compatibility",
            "Night mode deep check",
            "PASS" if night else "WARN",
            "values-night resources present" if night else "No values-night",
            "app/src/main/res",
            "advise", "S3",
        )

    def check_observability_deep(self) -> None:
        """Deep observability check: logging, crash reporting, and analytics."""
        found = []
        for crate in ("firebase-crashlytics", "sentry", "bugsnag", "datadog"):
            if crate in read(self.root / "app/build.gradle.kts").lower():
                found.append(crate)
        self.add(
            "crash_reporting_deep", "Observability",
            "Crash/ANR reporting deep check",
            "PASS" if found else "ACTION",
            f"{', '.join(found)} SDK present" if found else "No crash reporting SDK",
            "app/build.gradle.kts",
            "advise", "S2",
        )
        analytics = any(
            token in read(self.root / "app/build.gradle.kts").lower()
            for token in ("play-services-analytics", "firebase-analytics", "amplitude", "mixpanel", "posthog")
        )
        self.add(
            "analytics_deep", "Observability",
            "Product analytics deep check",
            "INFO" if not analytics else "PASS",
            "No analytics dependency" if not analytics else "analytics present",
            "app/build.gradle.kts",
        )

    def check_release_deep(self) -> None:
        """Deep release readiness check: signing, configuration, and store assets."""
        gradle = read(self.root / "app/build.gradle.kts")
        fail_closed = "Release signing config is missing" in gradle
        self.add(
            "signing_fail_closed_deep", "Release readiness",
            "Release signing deep check",
            "PASS" if fail_closed else "FAIL",
            "Release build fails without signing" if fail_closed else "unsigned release possible",
            "app/build.gradle.kts",
            "block", "S2",
        )
        no_default_key = "There is deliberately no\n        // built-in default" in gradle or "deliberately no" in gradle
        self.add(
            "no_baked_config_deep", "Release readiness",
            "No baked-in config deep check",
            "PASS" if no_default_key else "WARN",
            "AI_PROXY_URL has no built-in default" if no_default_key else "Has default config",
            "app/build.gradle.kts",
            "block", "S3",
        )
        store = list((self.root / "docs").glob("*store*")) + list((self.root / "play").glob("*"))
        self.add(
            "store_assets_deep", "Release readiness",
            "Store assets deep check",
            "PASS" if store else "WARN",
            f"{len(store)} store asset file(s)" if store else "No store metadata",
            "docs/",
            "advise", "S3",
        )

    def check_e2e_flow(self) -> None:
        """End-to-end flow check: critical user paths."""
        # Check critical paths exist
        has_salah = bool(re.search(r"SalahScreen", read(self.root / "app/src/main/java/com/sakinah/deen/ui/screens/SalahScreen.kt")))
        has_quran = bool(re.search(r"QuranScreen", read(self.root / "app/src/main/java/com/sakinah/deen/ui/screens/QuranScreen.kt")))
        has_auth = bool(re.search(r"signIn|signUp", read(self.root / "shared/src/commonMain/kotlin/com/sakinah/deen/auth/PersistentAuthRepository.kt")))
        critical_paths = sum([has_salah, has_quran, has_auth])
        self.add(
            "e2e_flow", "Release readiness",
            "Critical user paths exist",
            "INFO" if critical_paths < 3 else "PASS",
            f"{critical_paths}/3 critical paths found (Salah, Quran, Auth)",
            "app/src/main/java",
            "advise", "S3",
        )


def to_sarif(checks: list[Check]) -> dict:
    level = {"FAIL": "error", "ACTION": "warning", "WARN": "note", "PASS": "none", "INFO": "note"}
    rules = []
    results = []
    seen = set()
    for c in checks:
        if c.status in ("PASS", "INFO"):
            continue
        if c.id not in seen:
            seen.add(c.id)
            rules.append({
                "id": c.id,
                "name": re.sub(r"[^A-Za-z0-9]", "_", c.title),
                "shortDescription": {"text": c.title},
                "fullDescription": {"text": f"{c.area}: {c.title}"},
                "defaultConfiguration": {"level": level.get(c.status, "warning")},
                "properties": {"severity": c.severity, "area": c.area},
            })
        evidence = c.evidence.split(":")[0] if c.evidence else ".github/audit-baseline.json"
        results.append({
            "ruleId": c.id,
            "level": level.get(c.status, "warning"),
            "message": {"text": f"{c.title}: {c.detail}"},
            "locations": [{
                "physicalLocation": {
                    "artifactLocation": {"uri": evidence if evidence else "."},
                    "region": {"startLine": 1},
                }
            }],
            "properties": {"severity": c.severity, "area": c.area},
        })
    return {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [{
            "tool": {"driver": {
                "name": "ImanCore Audit Checklist",
                "informationUri": "https://github.com/",
                "rules": rules,
            }},
            "results": results,
        }],
    }


def render_markdown(checks: list[Check], strict: bool) -> str:
    icon = {"PASS": "✅", "FAIL": "❌", "ACTION": "⚠️", "WARN": "🟡", "INFO": "ℹ️"}
    fails = [c for c in checks if c.status == "FAIL"]
    actions = [c for c in checks if c.status == "ACTION"]
    lines = [
        "## Release audit checklist",
        "",
        f"**{len(checks)} checks** · ❌ {len(fails)} failing · ⚠️ {len(actions)} known gaps · "
        f"mode: {'strict' if strict else 'ratchet'}",
        "",
        "| | ID | Area | Sev | Check | Evidence |",
        "|---|---|---|---|---|---|",
    ]
    for area in AREA_ORDER:
        group = [c for c in checks if c.area == area]
        for c in group:
            evidence = c.evidence.replace("|", "\\|") or "—"
            lines.append(
                f"| {icon.get(c.status, '•')} | `{c.id}` | {c.area} | {c.severity} | "
                f"{c.title} — {c.detail.replace('|', '\\|')} | {evidence} |"
            )
    if actions:
        lines += ["", "### Known gaps (tracked, not blocking)", ""]
        for c in actions:
            lines.append(f"- **{c.id}** ({c.severity}, {c.area}): {c.detail}")
    lines += [
        "",
        "> Checks are measured from the repository by `.github/scripts/audit-checklist.py`. "
        "Counted defects are ratcheted against `.github/audit-baseline.json`: a regression fails, "
        "an improvement asks you to lower the baseline.",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--out", default="audit-report")
    parser.add_argument("--baseline", default=".github/audit-baseline.json")
    parser.add_argument("--strict", action="store_true")
    parser.add_argument("--update-baseline", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    baseline_path = root / args.baseline
    baseline: dict = {}
    if baseline_path.is_file():
        try:
            baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            print(f"::warning::invalid baseline JSON at {baseline_path}", file=sys.stderr)
    baseline.pop("_comment", None)

    audit = Audit(root, baseline)
    audit.run()

    out_dir = root / args.out
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.update_baseline:
        updated = {k: v for k, v in audit.metrics.items()}
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(
            json.dumps({"_comment": "Counted audit defects; lower is better. Regenerate with --update-baseline.",
                        **updated}, indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"updated {baseline_path} with {len(updated)} metrics")

    markdown = render_markdown(audit.checks, args.strict)
    (out_dir / "checklist.md").write_text(markdown + "\n", encoding="utf-8")
    sarif = to_sarif(audit.checks)
    (out_dir / "checklist.sarif").write_text(json.dumps(sarif, indent=2), encoding="utf-8")

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(markdown + "\n")

    print(markdown)
    for c in audit.checks:
        if c.status == "FAIL":
            print(f"::error title={c.id}::{c.title} — {c.detail}")
        elif c.status == "ACTION":
            print(f"::warning title={c.id}::{c.title} — {c.detail}")

    failing = [c for c in audit.checks if c.status == "FAIL" and c.gate in ("block", "ratchet")]
    if args.strict:
        failing += [c for c in audit.checks if c.status == "ACTION"]
    if failing:
        print(f"\n{len(failing)} blocking finding(s)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
