"""Checks a release tag against Lena's version, and writes the release's notes.

Used by both release workflows (.gitea/workflows/release.yml and .github/workflows/release.yml):
    python3 scripts/release_notes.py <tag> <owner/repo on GitHub> <notes file>
(the tag is v<version>, whether the release was started from the Run workflow button or by pushing a tag).
The notes always point at the public copy on GitHub, also in a release made on Gitea.

1. The tag must name the version every part of the project carries (project_versions below): the app, the
   Home Assistant integration, both Open WebUI files and the voice bridge. A leading "v" is ignored. A
   mismatch fails the release, so an APK is never published under the wrong version.
2. Lena's versions are plain numbers (0.6.12), with no "-beta" or other suffix, and stay below 1.0 until the
   owner says otherwise; anything else is refused.
3. CHANGELOG.md must have a section for it ("## [0.6.12] - 2026-11-08"), matched on the whole version, so
   0.6.1 never takes 0.6.11's section.
4. The notes are short install steps for each part, then that changelog section. Links in the changelog are
   relative to the repository; they're made absolute (at the tag) so they work on the release page.

Prints, for the workflow:
    prerelease=false   (always: Lena has no pre-releases; printed so both workflows read it the same way)
    title=v0.6.12      (Lena's releases are titled with the plain version)
    apk=lena-v0.6.12.apk   (the APK's name in the release; the app's updater takes any .apk it finds)
Exits non-zero, with the reason, when the release must not go ahead.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAIN_VERSION = re.compile(r"\d+\.\d+\.\d+")


def _search(path, pattern):
    """The first group of `pattern` in a file of the repository, or None when it isn't there."""
    match = re.search(pattern, (ROOT / path).read_text(encoding="utf-8"), re.M)
    return match.group(1) if match else None


def project_versions():
    """Every place the project's one version is written, and what each says. They must all agree;
    tools/check.py checks the same on every change, this checks it again at release time."""
    manifest = json.loads((ROOT / "custom_components/lena/manifest.json").read_text(encoding="utf-8"))
    return {
        "android/app/build.gradle.kts (versionName)": _search("android/app/build.gradle.kts", r'versionName = "([^"]+)"'),
        "custom_components/lena/manifest.json": manifest.get("version"),
        "open-webui/lena_filter.py": _search("open-webui/lena_filter.py", r"^version: *(\S+)"),
        "open-webui/lena_tools.py": _search("open-webui/lena_tools.py", r"^version: *(\S+)"),
        "voice/bridge/bridge.py (User-Agent)": _search("voice/bridge/bridge.py", r"LenaVoiceBridge/([^\"'\s]+)"),
    }


def changelog_section(version, text=None):
    """CHANGELOG.md's section for `version` (the text under "## [<version>] - <date>"), or None.
    The version has to match the heading's whole version: 0.6.1 doesn't take 0.6.11's section.
    `text` is the changelog to read, for the tests; normally the repository's."""
    if text is None:
        text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    heading = re.compile(rf"^## \[?{re.escape(version)}\]?(?=\s|$).*$", re.M)
    start = heading.search(text)
    if not start:
        return None
    following = re.compile(r"^## ", re.M).search(text, start.end())
    end = following.start() if following else len(text)
    return text[start.end():end].strip()


# Every release's changelog section ends with what to update, one line per part, so people with many
# moving parts know at a glance what to do: "Modelfile: No update needed (oldest working v0.6.31)",
# "Lena HA Integration: Need to update". A part that needs no update names the oldest version of it
# that still works: the last release that said "Need to update" for it. The Modelfile and the Open
# WebUI files are updated by hand, so that saves reading every release since to find out whether yours
# is new enough. tools/check.py checks the newest section has every line, and that each version named
# follows from the sections before it.
UPDATE_PARTS = (
    "Modelfile", "Lena HA Integration", "Lena Android App", "Open WebUI Tools", "Open WebUI Filters", "Voice Bridge",
)
NEED_UPDATE = "Need to update"
NO_UPDATE = re.compile(r"No update needed \(oldest working v(\d+\.\d+\.\d+)\)")


def update_answers(section):
    """What a changelog section says for each part: "need", a version ("0.6.31", for "No update needed
    (oldest working v0.6.31)"), or None when the line is missing or says something else, such as a
    bare "No update needed" without the version."""
    answers = {}
    for part in UPDATE_PARTS:
        line = re.compile(rf"^\s*(?:[-*]\s*)?\**{re.escape(part)}\**:\s*\**(.+?)\**\s*$", re.M)
        found = line.search(section or "")
        said = found.group(1).strip() if found else ""
        no_update = NO_UPDATE.fullmatch(said)
        if said == NEED_UPDATE:
            answers[part] = "need"
        elif no_update:
            answers[part] = no_update.group(1)
        else:
            answers[part] = None
    return answers


def missing_update_lines(section):
    """The parts a changelog section doesn't say plainly whether to update (empty when it says for all)."""
    return [part for part, said in update_answers(section).items() if said is None]


def _version_key(version):
    """"0.6.9" as (0, 6, 9), so versions compare as numbers: 0.6.9 comes before 0.6.10."""
    return tuple(int(n) for n in version.split("."))


def oldest_working_problems(text=None):
    """Every "oldest working" version in CHANGELOG.md that doesn't follow from the releases before it,
    as sentences (empty when they all do). Going from the oldest release up: a part's oldest working
    version is the last release that said "Need to update" for it, and a release that says "No update
    needed" must name that one. The oldest section with a What to update list is where it starts, so
    its versions can only be checked to be older releases that exist. Sections without a list (from
    before the rule) are skipped. `text` is the changelog to read, for the tests."""
    if text is None:
        text = (ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    headings = list(re.finditer(r"^## \[?v?(\d+\.\d+\.\d+)\]?(?=\s|$).*$", text, re.M))
    versions = [h.group(1) for h in headings]
    problems = []
    oldest = {}  # part -> its oldest working version so far
    for version in sorted(versions, key=_version_key):
        section = changelog_section(version, text) or ""
        if "### What to update" not in section:
            continue
        for part, said in update_answers(section).items():
            if said == "need":
                oldest[part] = version
            elif said is None:
                continue  # missing_update_lines reports it for the newest section
            elif said not in versions or _version_key(said) >= _version_key(version):
                problems.append(f"{version}: {part} names v{said}, which isn't an earlier release")
            elif part in oldest and said != oldest[part]:
                problems.append(f"{version}: {part} names v{said}, but the last release that needed it was "
                                f"v{oldest[part]}")
            else:
                oldest[part] = said
    return problems


def absolute_links(text, repo, tag):
    """Relative links (docs/app/Installation.md, README.md#...) as links to the repository at the tag."""
    def fix(m):
        target = m[2]
        if re.match(r"^[a-z]+:", target) or target.startswith("#"):
            return m[0]  # already absolute, or a link within the page
        path, _, anchor = target.partition("#")
        if path in ("", "README.md"):
            url = f"https://github.com/{repo}/tree/{tag}"
        else:
            url = f"https://github.com/{repo}/blob/{tag}/{path}"
        return f"[{m[1]}]({url}{'#' + anchor if anchor else ''})"
    return re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", fix, text)


def release_title(version):
    """The release's name: the plain version with its v, as every Lena release has had ("v0.6.11")."""
    return f"v{version}"


def apk_name(version):
    """The APK's file name in the release, the same on Gitea and GitHub."""
    return f"lena-v{version}.apk"


def install_steps(repo, tag, version):
    """How to get each part of this release; every part ships in every release, and the changelog below
    says which of them changed."""
    apk_url = f"https://github.com/{repo}/releases/download/{tag}/{apk_name(version)}"
    return (
        "## Install\n\n"
        f"- **The app:** in Lena, Settings → tap **Software version** → **Updates**. For a first install, "
        f"download [{apk_name(version)}]({apk_url}) on an Android 16 phone and open it "
        "([Installation](docs/app/Installation.md)); `SHA256SUMS` below lets you check the file.\n"
        "- **Home Assistant integration:** update **Lena** under Settings → Updates (from the Marketplace) and restart "
        "Home Assistant, or copy `custom_components/lena` over the old folder "
        "([Installation](docs/home-assistant/Installation.md)).\n"
        "- **Open WebUI:** paste the new `open-webui/lena_filter.py` and `open-webui/lena_tools.py` over the "
        "old ones, if the changes below mention them ([Lena in Open WebUI](docs/open-webui/Setup.md)).\n"
        "- **Her model and the voice bridge:** rebuild them only if the changes below say so.\n\n"
    )


def main():
    """Check the tag, write the notes file, and tell the workflow the release's title, its APK's name and
    that it isn't a pre-release."""
    tag, repo, notes_file = sys.argv[1], sys.argv[2], Path(sys.argv[3])
    version = tag[1:] if tag[:1] in ("v", "V") else tag

    versions = project_versions()
    app = versions["android/app/build.gradle.kts (versionName)"]
    disagree = [f"{where} says {found}" for where, found in versions.items() if found != app]
    if disagree:
        sys.exit(f"The parts' versions disagree (the app is {app}): " + "; ".join(disagree) + ". Make them match first.")
    if not PLAIN_VERSION.fullmatch(version):
        sys.exit(f"{version} isn't a plain version like 0.6.12: Lena's versions have no suffix")
    if version != app:
        sys.exit(f"{version} isn't the project's version ({app}): release {app}, or change the version first")
    if not version.startswith("0."):
        sys.exit(f"{version} is 1.0 or above, which needs the owner's go-ahead first")
    section = changelog_section(version)
    if not section:
        sys.exit(f"CHANGELOG.md has no section for {version} (\"## [{version}] - <date>\"): add one first")

    notes = install_steps(repo, tag, version) + "## What's in it\n\n" + section + "\n"
    notes_file.write_text(absolute_links(notes, repo, tag), encoding="utf-8")
    print("prerelease=false")
    print(f"title={release_title(version)}")
    print(f"apk={apk_name(version)}")


if __name__ == "__main__":
    main()
