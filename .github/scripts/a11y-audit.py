#!/usr/bin/env python3
"""Device accessibility audit driven by the Android UI Automator hierarchy.

This is the automated equivalent of the accessibility section of the manual checklist:
it measures real rendered bounds instead of reading source code. For every captured screen it
reports interactive targets below 48 dp, unlabelled clickable images and unlabelled image
nodes, then walks the main navigation and onboarding CTAs.

Usage on a connected device/emulator:
    python3 .github/scripts/a11y-audit.py --out a11y-report

Offline parser self-test (no device):
    python3 .github/scripts/a11y-audit.py --xml fixture.xml --density 420 --out /tmp/a11y
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

NAV_LABELS = [
    "Salah", "Quran", "Hadith", "Scholar", "Tools",
    "Continue", "Next", "Get started", "Get Started", "Skip", "Skip for now",
    "Save & Continue", "Done", "Complete", "Finish", "Start",
]
MIN_DP = 48


def adb(*args: str, timeout: int = 90) -> str:
    result = subprocess.run(
        ["adb", *args], capture_output=True, text=True, timeout=timeout, check=False
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or f"adb {' '.join(args)} failed")
    return result.stdout


def density() -> int:
    try:
        match = re.search(r"(\d+)", adb("shell", "wm", "density"))
        return int(match.group(1)) if match else 420
    except Exception:
        return 420


def dump_xml(destination: Path) -> Path:
    adb("shell", "uiautomator", "dump", "/sdcard/window_dump.xml")
    adb("pull", "/sdcard/window_dump.xml", str(destination))
    return destination


def focused_package() -> str:
    """Package currently owning input focus, so we never audit the launcher by mistake."""
    try:
        window = adb("shell", "dumpsys", "window")
    except Exception:
        return ""
    match = re.search(r"mCurrentFocus=.*?([A-Za-z0-9_.]+)/", window)
    return match.group(1) if match else ""


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    numbers = [int(n) for n in re.findall(r"-?\d+", value or "")]
    if len(numbers) != 4:
        return 0, 0, 0, 0
    return numbers[0], numbers[1], numbers[2], numbers[3]


def analyse(path: Path, dpi: int) -> dict:
    root = ET.parse(path).getroot()
    nodes = list(root.iter("node"))
    small: list[dict] = []
    unlabelled: list[dict] = []
    missing_cd: list[dict] = []
    clickable_rects: list[tuple[int, int, int, int]] = []

    for node in nodes:
        x1, y1, x2, y2 = parse_bounds(node.get("bounds", ""))
        if node.get("clickable") == "true":
            clickable_rects.append((x1, y1, x2, y2))

    def absorbed(x1: int, y1: int, x2: int, y2: int) -> bool:
        """True when a larger clickable ancestor already provides the 48 dp target."""
        for (cx1, cy1, cx2, cy2) in clickable_rects:
            if (cx1, cy1, cx2, cy2) == (x1, y1, x2, y2):
                continue
            if cx1 <= x1 and cy1 <= y1 and cx2 >= x2 and cy2 >= y2:
                if (cx2 - cx1) * 160 / dpi >= MIN_DP and (cy2 - cy1) * 160 / dpi >= MIN_DP:
                    return True
        return False

    for node in nodes:
        bounds = node.get("bounds", "")
        x1, y1, x2, y2 = parse_bounds(bounds)
        width_dp = (x2 - x1) * 160 / dpi
        height_dp = (y2 - y1) * 160 / dpi
        label = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        klass = (node.get("class") or "").rsplit(".", 1)[-1]
        clickable = node.get("clickable") == "true"
        entry = {
            "class": klass,
            "label": label,
            "contentDesc": desc,
            "bounds": bounds,
            "widthDp": round(width_dp, 1),
            "heightDp": round(height_dp, 1),
        }
        if (
            clickable
            and (width_dp < MIN_DP or height_dp < MIN_DP)
            and (label or desc)
            and not absorbed(x1, y1, x2, y2)
        ):
            small.append(entry)
        if clickable and klass in ("ImageView", "ImageButton") and not desc and not label:
            unlabelled.append(entry)
        if klass in ("ImageView", "ImageButton") and not desc:
            missing_cd.append(entry)

    return {
        "nodes": len(nodes),
        "smallTargets": small,
        "unlabelledClickables": unlabelled,
        "imagesWithoutDescription": missing_cd,
    }


def tap_label(path: Path, labels: list[str]) -> str | None:
    root = ET.parse(path).getroot()
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        candidate = text or desc
        if candidate and any(candidate.casefold() == label.casefold() for label in labels):
            x1, y1, x2, y2 = parse_bounds(node.get("bounds", ""))
            if x2 > x1 and y2 > y1:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                return candidate
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="a11y-report")
    parser.add_argument("--xml", help="analyse a saved uiautomator dump instead of a device")
    parser.add_argument("--density", type=int, default=0)
    parser.add_argument("--max-screens", type=int, default=8)
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    dpi = args.density or (density() if not args.xml else 420)

    screens: list[tuple[str, dict]] = []
    if args.xml:
        fixture = Path(args.xml)
        screens.append((fixture.name, analyse(fixture, dpi)))
    else:
        stable = 0
        visited: set[str] = set()
        for step in range(args.max_screens):
            if focused_package() not in ("com.imancore.app", ""):
                # A dialog or the launcher took focus: bring the app forward and re-check once.
                subprocess.run(
                    ["adb", "shell", "am", "start", "-n",
                     "com.imancore.app/com.sakinah.deen.MainActivity"],
                    check=False, capture_output=True,
                )
                subprocess.run(["sleep", "3"], check=False)
            try:
                dump = dump_xml(out / f"screen-{step}.xml")
                data = analyse(dump, dpi)
            except Exception as exc:  # an unreadable hierarchy is a tooling gap, not a pass
                print(f"::warning::could not capture screen {step}: {exc}")
                break
            fingerprint = f"{data['nodes']}:{len(data['smallTargets'])}:{len(data['unlabelledClickables'])}"
            label = tap_label(dump, NAV_LABELS)
            screens.append((f"screen-{step} ({label or 'no navigation target'})", data))
            if fingerprint in visited:
                stable += 1
                if stable >= 2:
                    break
            visited.add(fingerprint)
            if not label:
                break
            subprocess.run(["sleep", "2"], check=False)

        if not screens:
            message = "No screens could be captured; the accessibility audit did not run."
            (out / "a11y.md").write_text(f"### Device accessibility audit\n\n{message}\n", encoding="utf-8")
            print(f"::error::{message}")
            return 2

    total_small = sum(len(d["smallTargets"]) for _, d in screens)
    total_unlabelled = sum(len(d["unlabelledClickables"]) for _, d in screens)
    total_images = sum(len(d["imagesWithoutDescription"]) for _, d in screens)

    lines = [
        "### Device accessibility audit (48 dp / labels)",
        "",
        f"{len(screens)} screen capture(s) · {total_small} small target(s) · "
        f"{total_unlabelled} unlabelled clickable(s) · {total_images} image node(s) without a description",
        "",
    ]
    for name, data in screens:
        lines.append(f"#### {name}")
        lines.append(f"- {data['nodes']} nodes, {len(data['smallTargets'])} small targets, "
                     f"{len(data['unlabelledClickables'])} unlabelled clickables")
        for item in data["smallTargets"][:10]:
            lines.append(f"  - `{item['class']}` \"{item['label'] or item['contentDesc']}\" "
                         f"{item['widthDp']}×{item['heightDp']} dp at {item['bounds']}")
        for item in data["unlabelledClickables"][:10]:
            lines.append(f"  - unlabelled `{item['class']}` at {item['bounds']}")
        lines.append("")
    (out / "a11y.md").write_text("\n".join(lines), encoding="utf-8")
    (out / "a11y.json").write_text(json.dumps(
        [{"screen": name, **data} for name, data in screens], indent=2
    ), encoding="utf-8")

    print("\n".join(lines))
    for name, data in screens:
        for item in data["smallTargets"]:
            print(f"::error title=a11y-small-target::{name}: {item['class']} "
                  f"{item['widthDp']}x{item['heightDp']}dp at {item['bounds']}")
        for item in data["unlabelledClickables"]:
            print(f"::error title=a11y-unlabelled::{name}: unlabelled {item['class']} at {item['bounds']}")

    if args.xml:
        return 0
    return 1 if (total_small or total_unlabelled) else 0


if __name__ == "__main__":
    sys.exit(main())
