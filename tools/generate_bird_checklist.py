#!/usr/bin/env python3
"""Generate BirdChecklist.kt from the official Korean bird checklist text file.

Usage:
  python3 tools/generate_bird_checklist.py

Input:
  한반도_조류_전체목록_2025.txt

Output:
  app/src/main/java/com/pizzaandbird/game/BirdChecklist.kt
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "한반도_조류_전체목록_2025.txt"
OUTPUT = ROOT / "app/src/main/java/com/pizzaandbird/game/BirdChecklist.kt"
EXPECTED_SPECIES = 598
EXPECTED_SUBSPECIES = 383


def kstr(s: str) -> str:
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'


def parse_entries():
    order = None
    family = None
    entries = []
    for line in SOURCE.read_text(encoding="utf-8").splitlines():
        if line.startswith("[목] "):
            order = line[4:].strip()
        elif line.startswith("  [과] "):
            family = line[6:].strip()
        elif line.startswith("    - "):
            parts = [x.strip() for x in line[6:].split("|")]
            if len(parts) != 4:
                raise ValueError(f"Bad species line: {line!r}")
            entries.append(
                {
                    "order": order,
                    "family": family,
                    "korean": parts[0],
                    "scientific": parts[1],
                    "english": parts[2],
                    "category": parts[3],
                    "subspecies": [],
                }
            )
        elif line.startswith("      · 아종:"):
            if not entries:
                raise ValueError(f"Subspecies before species: {line!r}")
            entries[-1]["subspecies"].append(line.split(":", 1)[1].strip())
    return entries


def generate(entries):
    subspecies_count = sum(len(e["subspecies"]) for e in entries)
    if len(entries) != EXPECTED_SPECIES:
        raise ValueError(f"Expected {EXPECTED_SPECIES} species, got {len(entries)}")
    if subspecies_count != EXPECTED_SUBSPECIES:
        raise ValueError(f"Expected {EXPECTED_SUBSPECIES} subspecies, got {subspecies_count}")

    lines = [
        "package com.pizzaandbird.game",
        "",
        "/**",
        " * 한국조류학회 「한국조류목록 개정판 2025 v2.1」 기반 공식 조류 체크리스트.",
        " *",
        " * 원본: 한반도_조류_전체목록_2025.txt",
        " * 기준: 2025년 5월 말까지 한반도에서 기록된 조류, IOC World Bird List v15.1.",
        " * 공식 기록종 24목 85과 598종, 아종 383개. 보류종 제외.",
        " *",
        " * 이 파일은 원본 텍스트 목록에서 생성한 정적 데이터입니다.",
        " */",
        "class BirdChecklistEntry(",
        "    val orderName: String,",
        "    val familyName: String,",
        "    val koreanName: String,",
        "    val scientificName: String,",
        "    val englishName: String,",
        "    val category: String,",
        "    val subspecies: List<String> = emptyList()",
        ")",
        "",
        "object OfficialBirdChecklist {",
        "    const val SOURCE_TITLE = \"한국조류학회 조류목록 개정판 2025 v2.1\"",
        "    const val SPECIES_COUNT = 598",
        "    const val SUBSPECIES_COUNT = 383",
        "",
        "    val ALL: List<BirdChecklistEntry> = listOf(",
    ]
    for i, e in enumerate(entries):
        subs = e["subspecies"]
        subs_src = "emptyList()" if not subs else "listOf(" + ", ".join(kstr(s) for s in subs) + ")"
        comma = "," if i < len(entries) - 1 else ""
        lines.extend(
            [
                "        BirdChecklistEntry(",
                f"            {kstr(e['order'])}, {kstr(e['family'])}, {kstr(e['korean'])},",
                f"            {kstr(e['scientific'])}, {kstr(e['english'])}, {kstr(e['category'])},",
                f"            {subs_src}",
                f"        ){comma}",
            ]
        )
    lines.extend(
        [
            "    )",
            "",
            "    val byKoreanName: Map<String, BirdChecklistEntry> = ALL.associateBy { it.koreanName }",
            "    val byScientificName: Map<String, BirdChecklistEntry> = ALL.associateBy { it.scientificName }",
            "}",
            "",
        ]
    )
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")


def main():
    entries = parse_entries()
    generate(entries)
    print(f"Generated {OUTPUT.relative_to(ROOT)} ({len(entries)} species)")


if __name__ == "__main__":
    main()
