"""Checks the built APK the way Lena's own updater and Android's installer will look at it, so an APK they'd
refuse never gets published.

Used by both release workflows, after the build:
    python3 scripts/check_apk.py lena-v0.6.12.apk SHA256SUMS

Lena updates herself from her GitHub releases (Updates.kt): she takes the release's .apk, checks it against the
release's SHA256SUMS, its package name, its build number and the signing key of the app already installed,
before Android's installer sees it. An APK signed with the throwaway debug key (the build falls back to it
when the signing secrets are missing) installs over nothing, so it must never reach a release. This checks:

1. it's an APK that opens (a zip with every file intact) with what Android needs: AndroidManifest.xml,
   classes.dex and resources.arsc;
2. it's signed with Android's v2/v3 APK signature (the only kind for minSdk 36), and not with the debug key;
3. its manifest names Lena's package and the repository's version, so it's the build it claims to be;
4. its file name is the one the release notes link to, and SHA256SUMS lists it with its real checksum.

Python's standard library only, like the other release scripts. Exits non-zero, with every problem found,
when the APK isn't fit to publish.
"""
import hashlib
import struct
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import release_notes  # noqa: E402 - the scripts folder, added just above

PACKAGE = "io.github.soullink9.lena"
REQUIRED = ("AndroidManifest.xml", "classes.dex", "resources.arsc")
SIGNING_MAGIC = b"APK Sig Block 42"
V2_ID, V3_ID, V31_ID = 0x7109871A, 0xF05368C0, 0x1B93AD61  # Android's ids for the signature schemes
DEBUG_SUBJECT = b"Android Debug"  # the debug key's certificate is "CN=Android Debug, O=Android, C=US"


def signing_block(data):
    """The APK Signing Block, as {scheme id: value}, or None when there isn't one.

    Android puts it straight before the zip's central directory: pairs of (length, id, value), framed by its
    size and ending in the magic "APK Sig Block 42". The central directory's offset is in the zip's end record,
    the last 22 bytes of the file unless the zip has a comment (APKs don't, but searching back costs nothing).
    """
    end = data.rfind(b"PK\x05\x06")
    if end < 0 or end + 22 > len(data):
        return None
    directory = struct.unpack_from("<I", data, end + 16)[0]
    if directory < 24 or data[directory - 16:directory] != SIGNING_MAGIC:
        return None
    size = struct.unpack_from("<Q", data, directory - 24)[0]
    start = directory - size - 8
    if start < 0 or struct.unpack_from("<Q", data, start)[0] != size:
        return None
    pairs, at = {}, start + 8
    while at < directory - 24:
        length = struct.unpack_from("<Q", data, at)[0]
        scheme = struct.unpack_from("<I", data, at + 8)[0]
        pairs[scheme] = data[at + 12:at + 8 + length]
        at += 8 + length
    return pairs


def manifest_strings(binary_xml):
    """Every string in a compiled AndroidManifest.xml (the package name and versionName are among them).

    The build compiles the manifest to Android's binary XML, whose first chunk after the file header is a
    string pool: a count, a flag for UTF-8 or UTF-16, and offsets to each string. That's all this needs, so
    it reads just that, rather than the whole format.
    """
    if len(binary_xml) < 36 or struct.unpack_from("<H", binary_xml, 0)[0] != 0x0003:
        return []
    pool = 8
    if struct.unpack_from("<H", binary_xml, pool)[0] != 0x0001:
        return []
    count, _styles, flags, strings_start = struct.unpack_from("<IIII", binary_xml, pool + 8)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from(f"<{count}I", binary_xml, pool + 28)
    found = []
    for offset in offsets:
        at = pool + strings_start + offset
        if utf8:
            # Two lengths, characters then bytes, each one byte, or two when the high bit is set.
            at += 2 if binary_xml[at] & 0x80 else 1
            length = binary_xml[at]
            if length & 0x80:
                length = ((length & 0x7F) << 8) | binary_xml[at + 1]
                at += 1
            found.append(binary_xml[at + 1:at + 1 + length].decode("utf-8", errors="replace"))
        else:
            length = struct.unpack_from("<H", binary_xml, at)[0]
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | struct.unpack_from("<H", binary_xml, at + 2)[0]
                at += 2
            found.append(binary_xml[at + 2:at + 2 + length * 2].decode("utf-16-le", errors="replace"))
    return found


def problems_in(apk_path, sums_path=None):
    """Everything about the APK that would stop a release (an empty list: it's fine)."""
    apk_path = Path(apk_path)
    version = release_notes.project_versions()["android/app/build.gradle.kts (versionName)"]
    try:
        archive = ZipFile(apk_path)
    except (OSError, BadZipFile) as e:
        return [f"not an APK that opens: {e}"]
    problems = []
    with archive:
        broken = archive.testzip()
        if broken:
            return [f"damaged file in the APK: {broken}"]
        names = set(archive.namelist())
        problems += [f"missing {required}" for required in REQUIRED if required not in names]
        if "AndroidManifest.xml" in names:
            strings = manifest_strings(archive.read("AndroidManifest.xml"))
            if PACKAGE not in strings:
                problems.append(f"its manifest doesn't name {PACKAGE}: it isn't Lena")
            if version not in strings:
                problems.append(f"its manifest doesn't say version {version}, the repository's: rebuild it")

    data = apk_path.read_bytes()
    block = signing_block(data)
    if block is None or not {V2_ID, V3_ID, V31_ID} & set(block):
        problems.append("not signed with an APK signature (v2/v3): Android won't install it")
    elif any(DEBUG_SUBJECT in value for value in block.values()):
        problems.append("signed with the debug key: set the KEYSTORE_B64 and KEYSTORE_PASSWORD secrets")

    if apk_path.name != release_notes.apk_name(version):
        problems.append(f"named {apk_path.name}, but the release notes link to {release_notes.apk_name(version)}")
    if sums_path is not None:
        digest = hashlib.sha256(data).hexdigest()
        listed = {}
        for line in Path(sums_path).read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) >= 2:
                listed[parts[-1].lstrip("*")] = parts[0].lower()
        if apk_path.name not in listed:
            problems.append(f"SHA256SUMS doesn't list {apk_path.name}: the app's updater would refuse it")
        elif listed[apk_path.name] != digest:
            problems.append(f"SHA256SUMS has the wrong checksum for {apk_path.name}")
    return problems


def main():
    """Check the APK (and SHA256SUMS, if given) named on the command line, and report."""
    if len(sys.argv) < 2:
        sys.exit("Usage: check_apk.py <apk> [SHA256SUMS]")
    apk_path = sys.argv[1]
    problems = problems_in(apk_path, sys.argv[2] if len(sys.argv) > 2 else None)
    if problems:
        sys.exit(f"{apk_path} isn't fit to release:\n  " + "\n  ".join(problems))
    print(f"{apk_path}: Lena {release_notes.project_versions()['android/app/build.gradle.kts (versionName)']}, "
          "signed with the release key, listed in SHA256SUMS")


if __name__ == "__main__":
    main()
