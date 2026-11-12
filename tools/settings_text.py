"""Lists every title and detail in Lena's Settings, for reading them against docs/app/Writing.md.

    python3 tools/settings_text.py           every item: line, title, detail
    python3 tools/settings_text.py --look    only the ones worth a look

Worth a look: a detail over 90 characters, "phone" in the text (is it true on a TV or tablet?), and a title
used more than once. It reads SettingsActivity.kt's Item, SwitchItem and PickItem calls; text built from
values (a current setting) shows as written in the code.
"""
import re
import sys
from collections import Counter
from pathlib import Path

SETTINGS = Path(__file__).resolve().parent.parent / "android/app/src/main/kotlin/io/github/soullink9/lena/SettingsActivity.kt"
ITEM = re.compile(r'\b(?:Switch|Pick)?Item\([^,\n]*,\s*"((?:[^"\\]|\\.)*)"(?:,\s*"((?:[^"\\]|\\.)*)")?')
LONG = 90


def items():
    """(line number, title, detail or "") for each item in Settings, in the order of the file."""
    found = []
    for number, line in enumerate(SETTINGS.read_text(encoding="utf-8").splitlines(), 1):
        for match in ITEM.finditer(line):
            found.append((number, match.group(1), match.group(2) or ""))
    return found


def worth_a_look(title, detail, times):
    """Why this item deserves a read, as a list of reasons (empty: nothing stands out)."""
    reasons = []
    if len(detail) > LONG:
        reasons.append(f"detail is {len(detail)} characters")
    if re.search(r"\bphone\b", title + " " + detail, re.I):
        reasons.append("says phone")
    if times[title] > 1:
        reasons.append(f"title used {times[title]} times")
    return reasons


def main(args):
    found = items()
    times = Counter(title for _, title, _ in found)
    only_look = args == ["--look"]
    for number, title, detail in found:
        reasons = worth_a_look(title, detail, times)
        if only_look and not reasons:
            continue
        note = f"   [{'; '.join(reasons)}]" if reasons else ""
        print(f"{number:5}  {title}" + (f"  |  {detail}" if detail else "") + note)
    print(f"\n{len(found)} items in Settings.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
