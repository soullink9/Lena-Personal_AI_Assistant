#!/usr/bin/env python3
"""Rebuilds the package lists at the end of tools/wall-display-debloat.sh.

The lists come from the Universal Android Debloater Next Generation project (GPL-3.0):
https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation
Download its resources/assets/uad_lists.json yourself (nothing here goes online), then:

    python3 tools/debloat_lists.py path/to/uad_lists.json

Which of its packages the script removes (the REMOVE list):

- Samsung, Google and AOSP packages it rates "Recommended" or "Advanced";
- carrier and miscellaneous preloads it rates "Recommended" (their other tiers are chip-vendor radio,
  IMS and power services);
- the few "Expert" ones in EXPERT_PICKS below, checked to be safe on a Wi-Fi tablet;
- never one it rates "Unsafe", and never one in the script's own PROTECT list.

The KNOWN list is every other Samsung, Google, AOSP or chip-vendor package it rates, so the script's
preview can tell packages it left alone on purpose from ones nobody has rated yet.

Review the new lists before committing them (git diff), and run the script's preview on a tablet.

Standard library only.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent / "wall-display-debloat.sh"

# Where the lists start in the script; everything from here to the end is rewritten.
LISTS_START = ": <<'__END__'"

# "Expert" packages that are safe on a Wi-Fi wall tablet. The rest of that tier (Wi-Fi, Bluetooth,
# the navigation bar, media storage, the share sheet, the launcher, software updates…) stays.
EXPERT_PICKS = {
    "com.sec.android.app.samsungapps",      # Galaxy Store
    "com.samsung.android.app.routines",     # Modes and Routines
    "com.samsung.android.contacts",         # Samsung Contacts
    "com.samsung.android.sume.nn.service",  # Gallery's photo remaster
    "com.android.dynsystem",                # Dynamic System Updates (for testing other system images)
    "com.google.android.contactkeys",       # Android System Key Verifier
    "com.android.backupconfirm",            # backup restore confirmation
}

# Samsung's packages are in UAD's "Oem" list with every other manufacturer's; these prefixes pick them out.
SAMSUNG = re.compile(
    r"^(com\.samsung|com\.sec\.|com\.osp\.|com\.wssyncmldm|com\.wsomacp|com\.knox|com\.skms|com\.mobeam|"
    r"com\.diotek|com\.monotype|com\.dsi\.ant|com\.sem\.|android\.autoinstalls)"
)

# Packages worth naming in the KNOWN list: the ones that can be on a Samsung tablet.
RELEVANT = re.compile(
    r"^(com\.samsung|com\.sec\.|com\.google|com\.android|android\.|com\.osp\.|com\.wssyncmldm|com\.wsomacp|"
    r"com\.knox|com\.skms|com\.qualcomm|com\.qti|vendor\.qti|com\.sem\.|com\.monotype|com\.dsi)"
)


def protected() -> set[str]:
    """The PROTECT list in the script, so a protected package never shows up as one to remove."""
    text = SCRIPT.read_text(encoding="utf-8")
    block = re.search(r"^PROTECT=\($(.*?)^\)$", text, re.M | re.S)
    if block is None:
        sys.exit("Couldn't find PROTECT=( ... ) in the script.")
    names = set()
    for line in block.group(1).splitlines():
        line = line.split("#", 1)[0].strip()
        if line:
            names.add(line)
    return names


def removable(name: str, entry: dict) -> bool:
    """Whether the script removes this package (see the rules at the top)."""
    tier, source = entry["removal"], entry["list"]
    if name in EXPERT_PICKS:
        return True
    if tier not in ("Recommended", "Advanced"):
        return False
    if source in ("Google", "Aosp"):
        return True
    if source == "Oem":
        return bool(SAMSUNG.match(name))
    if source in ("Misc", "Carrier"):
        return tier == "Recommended"
    return False


def summary(description: str) -> str:
    """The first sentence of UAD's description, without links, short enough for one preview line."""
    text = re.sub(r"\s+", " ", description).strip()
    text = re.sub(r"\(?https?://\S+\)?", "", text).strip()
    first = re.split(r"(?<=[.!?])\s", text, maxsplit=1)[0]
    return first.replace("|", "/")[:72].rstrip()


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit("Usage: python3 tools/debloat_lists.py path/to/uad_lists.json")
    uad = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    keep = protected()

    remove = []
    known = []
    for name, entry in sorted(uad.items()):
        if removable(name, entry) and name not in keep:
            remove.append(f"{name}|{entry['removal']}|{summary(entry['description'])}")
        elif RELEVANT.match(name):
            known.append(name)

    text = SCRIPT.read_text(encoding="utf-8")
    start = text.find("\n" + LISTS_START + "\n")
    if start == -1:
        sys.exit(f"Couldn't find the lists ({LISTS_START}) in the script.")
    lists = "\n".join([LISTS_START, "__REMOVE__", *remove, "__KNOWN__", *known, "__END__"]) + "\n"
    SCRIPT.write_text(text[: start + 1] + lists, encoding="utf-8")
    print(f"{len(remove)} packages to remove, {len(known)} known and left alone. Review: git diff {SCRIPT.name}")


if __name__ == "__main__":
    main()
