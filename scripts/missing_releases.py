"""Which of Lena's versions GitHub has a tag for but no release yet, for .github/workflows/catch-up-releases.yml.

Gitea is where releases are made, and its push mirror brings the tags to GitHub every few hours. A new tag
normally starts GitHub's release workflow (.github/workflows/release.yml) by itself, but not always: GitHub
starts nothing for the tags of a push that brings more than three at once (several releases between two
mirror pushes), and a run can be missed. So after each mirror push, and every few hours, the catch-up workflow
asks this which releases are missing and starts the release workflow for each of them.

    python3 scripts/missing_releases.py [--retry-failed] <tags file> <releases file> <runs file>
        tags file:     one tag per line (git ls-remote --tags --refs, names only)
        releases file: gh release list --json tagName,isDraft
        runs file:     gh run list --workflow release.yml --json headBranch,displayTitle,status,conclusion
                       (a run is for the version in its title, "Release 0.6.17" or "Release v0.6.17", or its
                       branch, which is the tag for a run a tag started)
        --retry-failed: start again the versions whose only release runs failed, instead of reporting them
    Prints one line per action:
        release v0.6.17                 start the release workflow for this tag
        warning <text>                  something to look at (a release run that failed)

    python3 scripts/missing_releases.py --latest <tag> <releases file>
    Prints true when <tag> is the newest of Lena's versions with a release (counting itself; other tags' releases
    don't count), else false:
    release.yml marks only that one Latest, so releases started together in any order leave the newest on top.

Which tags count: only Lena's versions, the plain numbers release_notes.py accepts (v0.6.17), with a section in
CHANGELOG.md. That leaves out the old -beta tags and the tags kept from the repositories merged into this one
(v2.x), which never get a release. Of those, only versions newer than the newest one already released: an old
version without a release was left that way on purpose. With no release on GitHub at all, only the newest.
A tag with a release run already (queued, running or finished) is left to that run; one that failed is
reported, not started again every few hours (once it's fixed, the catch-up's Run workflow button with "start
again the ones that failed" ticked starts it again).
At most three are started at once, oldest first.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import release_notes  # noqa: E402 - this folder, added just above

TAG = re.compile(r"^[vV](\d+)\.(\d+)\.(\d+)$")
MOST_AT_ONCE = 3


def version_of(tag):
    """(0, 6, 17) for "v0.6.17", or None for a tag that isn't one of Lena's plain versions."""
    match = TAG.match(tag.strip())
    return tuple(int(part) for part in match.groups()) if match else None


def lenas_versions(tags, changelog):
    """The tags that are Lena's versions (plain, with a changelog section), oldest first."""
    versions = []
    for tag in tags:
        version = version_of(tag)
        if version and release_notes.changelog_section(".".join(map(str, version)), changelog) is not None:
            versions.append((version, tag.strip()))
    return [tag for _, tag in sorted(versions)]


def published(releases):
    """The tags with a published release; a draft doesn't count, as the release workflow publishes it."""
    return {r["tagName"] for r in releases if not r.get("isDraft")}


def run_version(run):
    """The version a release run is for, (0, 6, 17), from its title or its branch; None if neither says."""
    for text in (run.get("displayTitle") or "", run.get("headBranch") or ""):
        match = re.search(r"\b[vV]?(\d+\.\d+\.\d+)\b", text)
        if match:
            return version_of("v" + match.group(1))
    return None


def to_release(tags, releases, runs, changelog, retry_failed=False):
    """The actions to take: ("release", tag) for each version to start, ("warning", text) for each failed run."""
    versions = lenas_versions(tags, changelog)
    done = published(releases)
    released = [tag for tag in versions if tag in done]
    if released:
        newest = version_of(released[-1])
        missing = [tag for tag in versions if tag not in done and version_of(tag) > newest]
    else:
        missing = versions[-1:]
    runs_by_version = {}
    for run in runs:
        runs_by_version.setdefault(run_version(run), []).append(run)
    actions = []
    for tag in missing:
        earlier = runs_by_version.get(version_of(tag), [])
        failed = bool(earlier) and all(run.get("status") == "completed" and run.get("conclusion") not in ("success", None)
                                       for run in earlier)
        if not earlier or (failed and retry_failed):
            actions.append(("release", tag))
        elif failed:
            actions.append(("warning", f"The release workflow for {tag} didn't succeed, so GitHub has no {tag} release. "
                                       "Fix what it says, then run Catch up releases with \"Also start again the versions "
                                       "whose release run failed\" ticked."))
    starts = [action for action in actions if action[0] == "release"]
    if len(starts) > MOST_AT_ONCE:
        later = [tag for _, tag in starts[MOST_AT_ONCE:]]
        actions = [a for a in actions if a[0] != "release" or a[1] not in later]
        actions.append(("warning", f"Left for the next run, to start at most {MOST_AT_ONCE} at once: {', '.join(later)}"))
    return actions


def is_latest(tag, releases, changelog):
    """Whether `tag` is the newest of Lena's versions among the published releases and itself. Releases of
    other tags (v2.x, kept from the merged repositories) don't count."""
    version = version_of(tag)
    if version is None:
        return False
    others = [version_of(t) for t in lenas_versions(published(releases), changelog)]
    return all(other <= version for other in others)


def main(args):
    changelog = (release_notes.ROOT / "CHANGELOG.md").read_text(encoding="utf-8")
    if args[:1] == ["--latest"] and len(args) == 3:
        releases = json.loads(Path(args[2]).read_text(encoding="utf-8") or "[]")
        print("true" if is_latest(args[1], releases, changelog) else "false")
        return 0
    retry_failed = args[:1] == ["--retry-failed"]
    if retry_failed:
        args = args[1:]
    if len(args) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    tags = Path(args[0]).read_text(encoding="utf-8").split()
    releases = json.loads(Path(args[1]).read_text(encoding="utf-8") or "[]")
    runs = json.loads(Path(args[2]).read_text(encoding="utf-8") or "[]")
    for kind, text in to_release(tags, releases, runs, changelog, retry_failed):
        print(f"{kind} {text}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
