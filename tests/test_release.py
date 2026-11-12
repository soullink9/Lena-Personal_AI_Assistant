"""Release scripts: the version check and notes (scripts/release_notes.py), the APK check (scripts/check_apk.py),
publishing on Gitea (scripts/publish_gitea_release.py) against a fake Gitea server, and which releases GitHub is
missing (scripts/missing_releases.py). These run in both release
workflows, so a mistake here would stop or spoil a release.

Run from the repository root:  python3 tests/test_release.py   (tools/check.py runs it too)
Python's standard library only; nothing is built, downloaded or published for real.
"""
import hashlib
import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import threading
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import check_apk  # noqa: E402 - the scripts folder, added just above
import publish_gitea_release as publish  # noqa: E402,F401 - imported so a syntax error fails here too
import missing_releases  # noqa: E402
import release_notes  # noqa: E402

REPO = "soullink9/Lena-Personal_AI_Assistant"
fails = 0


def check(ok, msg):
    """Print a pass or a failure, and count the failures."""
    global fails
    print(("PASS " if ok else "FAIL ") + msg)
    fails += 0 if ok else 1


tmp = Path(tempfile.mkdtemp())
version = release_notes.project_versions()["android/app/build.gradle.kts (versionName)"]
tag = f"v{version}"

# --- release_notes.py: the tag must match every part's version, and the notes give the install steps -----------


def notes_script(tag, notes_file="notes.md"):
    """Run the script as the workflows do; returns (exit code, what it printed, its error)."""
    out = subprocess.run([sys.executable, str(ROOT / "scripts/release_notes.py"), tag, REPO, str(tmp / notes_file)],
                         capture_output=True, text=True)
    return out.returncode, out.stdout, out.stderr


versions = release_notes.project_versions()
check(len(versions) == 5 and set(versions.values()) == {version},
      f"the app, the integration, both Open WebUI files and the voice bridge all say {version}")
code, out, err = notes_script(tag)
notes = (tmp / "notes.md").read_text() if code == 0 else ""
check(code == 0 and f"title=v{version}" in out and "prerelease=false" in out and f"apk=lena-v{version}.apk" in out,
      f"the project's own version: notes written, title, APK name and pre-release given to the workflow ({out.strip()!r} {err.strip()!r})")
apk_url = f"https://github.com/{REPO}/releases/download/{tag}/lena-v{version}.apk"
check(notes.startswith("## Install") and apk_url in notes and "## What's in it" in notes,
      "notes give the install steps with the APK's GitHub download address, then the changelog")
check(release_notes.absolute_links(release_notes.changelog_section(version), REPO, tag) in notes,
      "…and the whole of this version's changelog section (with its links made absolute)")
check(not re.search(r"\]\((?!https?:|#)", notes), "every link in the notes is absolute, so it works on the release page")
check(f"https://github.com/{REPO}/blob/{tag}/docs/app/Installation.md" in notes, "…pointing at GitHub, at the tag")
check(notes_script(f"V{version}", "notes-capital.md")[0] == 0, "a capital V typed by mistake is fine")

code, _, err = notes_script("v9.9.9")
check(code != 0 and "isn't the project's version" in err, f"another version: refused ({err.strip()})")
code, _, err = notes_script(f"v{version}-beta")
check(code != 0 and "plain version" in err, f"a version with a suffix: refused, Lena has none ({err.strip()})")

changelog = "## [Unreleased]\n\n- next\n\n## [0.6.11] - 2026-11-07\n\nEleven.\n\n## [0.6.1] - 2026-11-01\n\nOne.\n"
check(release_notes.changelog_section("0.6.1", changelog) == "One." and release_notes.changelog_section("0.6.11", changelog) == "Eleven.",
      "the changelog is matched on the whole version: 0.6.1 doesn't take 0.6.11's section")
check(release_notes.changelog_section("0.6.12", changelog) is None, "a version with no changelog section: none found")
check(release_notes.changelog_section("0.5.2", "## 0.5.2 (2026-11-01)\n\nOld style.\n") == "Old style.",
      "a heading without brackets is read too")
check(release_notes.release_title("0.6.12") == "v0.6.12" and release_notes.apk_name("0.6.12") == "lena-v0.6.12.apk",
      "release title v0.6.12 and APK lena-v0.6.12.apk, as every Lena release has had")
check(release_notes.absolute_links("[a](README.md#b) [c](https://x.y) [d](#e)", REPO, "v1")
      == f"[a](https://github.com/{REPO}/tree/v1#b) [c](https://x.y) [d](#e)",
      "README links go to the repository's front page; absolute links and anchors are left alone")

# --- check_apk.py: what Lena's updater and Android's installer accept ------------------------------------------


def binary_manifest(strings, utf8=True):
    """A compiled AndroidManifest.xml with just its string pool: what check_apk.py reads from it."""
    encoded = []
    for s in strings:
        if utf8:
            raw = s.encode()
            encoded.append(bytes([len(s), len(raw)]) + raw + b"\0")
        else:
            encoded.append(struct.pack("<H", len(s)) + s.encode("utf-16-le") + b"\0\0")
    offsets, at = [], 0
    for e in encoded:
        offsets.append(at)
        at += len(e)
    data = b"".join(encoded)
    data += b"\0" * (-len(data) % 4)
    header = 28 + 4 * len(strings)
    pool = struct.pack("<HHIIIIII", 0x0001, 28, header + len(data), len(strings), 0, 0x100 if utf8 else 0, header, 0)
    pool += struct.pack(f"<{len(strings)}I", *offsets) + data
    return struct.pack("<HHI", 0x0003, 8, 8 + len(pool)) + pool


def signing_block(pairs):
    """An APK Signing Block holding these (id, value) pairs, framed as Android writes it."""
    body = b"".join(struct.pack("<QI", 4 + len(value), scheme) + value for scheme, value in pairs)
    size = len(body) + 8 + 16
    return struct.pack("<Q", size) + body + struct.pack("<Q", size) + check_apk.SIGNING_MAGIC


made = 0


def make_apk(name, manifest=None, pairs=((check_apk.V2_ID, b"certificate: CN=Lena"),), files=None):
    """An APK-shaped file: a zip with Android's files, and (unless pairs is empty) a signing block put straight
    before its central directory, with the directory's offset moved to match. Each goes in a folder of its
    own, so APKs with the same name don't replace each other."""
    global made
    made += 1
    (tmp / f"apk{made}").mkdir()
    path = tmp / f"apk{made}" / name
    contents = {"AndroidManifest.xml": manifest if manifest is not None else binary_manifest(
        ["versionCode", "versionName", check_apk.PACKAGE, version, "android"]),
        "classes.dex": b"dex\n035\0", "resources.arsc": b"\x02\0\x0c\0"}
    contents.update(files or {})
    with zipfile.ZipFile(path, "w") as z:
        for member, data in contents.items():
            if data is not None:
                z.writestr(member, data)
    data = path.read_bytes()
    if pairs:
        end = data.rfind(b"PK\x05\x06")
        directory = struct.unpack_from("<I", data, end + 16)[0]
        block = signing_block(pairs)
        data = data[:directory] + block + data[directory:]
        end += len(block)
        data = data[:end + 16] + struct.pack("<I", directory + len(block)) + data[end + 20:]
        path.write_bytes(data)
    return path


def sums_for(*paths, wrong=False):
    """A SHA256SUMS file as the build writes it (sha256sum's format), optionally with a wrong checksum."""
    sums = tmp / "SHA256SUMS"
    lines = [f"{'0' * 64 if wrong else hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}" for p in paths]
    sums.write_text("\n".join(lines) + "\n")
    return sums


good = make_apk(f"lena-v{version}.apk")
check(check_apk.problems_in(good, sums_for(good)) == [], f"a signed Lena {version} APK listed in SHA256SUMS passes")
check(zipfile.ZipFile(good).testzip() is None and check_apk.signing_block(good.read_bytes()) is not None,
      "…(the test APK is a sound zip with its signing block where Android reads it)")
utf16 = make_apk(f"lena-v{version}.apk", manifest=binary_manifest([check_apk.PACKAGE, version], utf8=False))
check(check_apk.problems_in(utf16) == [], "a manifest with a UTF-16 string pool is read too")
check(any("debug key" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk",
      pairs=((check_apk.V2_ID, b"CN=Android Debug, O=Android, C=US"),)))), "signed with the debug key: refused")
check(any("APK signature" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk", pairs=()))),
      "not signed at all: refused")
check(any("APK signature" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk", pairs=((0x42726577, b"pad"),)))),
      "a signing block with only padding in it: refused")
check(any("rebuild" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk",
      manifest=binary_manifest([check_apk.PACKAGE, "0.0.1"])))), "an APK of another version: refused")
check(any("isn't Lena" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk",
      manifest=binary_manifest(["com.example.other", version])))), "another app's APK: refused")
check(any("missing classes.dex" in p for p in check_apk.problems_in(make_apk(f"lena-v{version}.apk", files={"classes.dex": None}))),
      "an APK without its code: refused")
check(any("release notes link" in p for p in check_apk.problems_in(make_apk("app-release.apk"))),
      "the build's own name (app-release.apk): refused, the notes link to lena-v<version>.apk")
check(any("doesn't list" in p for p in check_apk.problems_in(good, sums_for(make_apk("other.apk")))),
      "SHA256SUMS without this APK: refused, the updater would stop on it")
check(any("wrong checksum" in p for p in check_apk.problems_in(good, sums_for(good, wrong=True))),
      "SHA256SUMS with the wrong checksum: refused")
(tmp / "page.apk").write_text("<html>release page</html>")
check(check_apk.problems_in(tmp / "page.apk") != [], "a web page saved as .apk: refused")
result = subprocess.run([sys.executable, str(ROOT / "scripts/check_apk.py"), str(good), str(sums_for(good))],
                        capture_output=True, text=True)
check(result.returncode == 0, f"run as the workflows run it: passes ({result.stdout.strip()}{result.stderr.strip()})")

# --- publish_gitea_release.py against a fake Gitea ------------------------------------------------------------


class FakeGitea(BaseHTTPRequestHandler):
    """Just enough of Gitea's release API: releases by tag, create, edit, delete and upload attachments."""
    releases, calls, uploads, next_id = {}, [], {}, 1  # uploads: asset id -> (bytes, sent as "attachment", type)

    def log_message(self, *args):
        pass

    def reply(self, code, body=None):
        data = json.dumps(body).encode() if body is not None else b""
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def handle_any(self):
        base = "/api/v1/repos/owner/lena"
        path = self.path.split("?")[0]
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else b""
        FakeGitea.calls.append((self.command, self.path, self.headers.get("Authorization")))
        if self.headers.get("Authorization") != "token secret":
            return self.reply(401, {"message": "token is required"})
        if not path.startswith(base):
            return self.reply(404, {"message": "not found"})
        path = path[len(base):]
        if self.command == "GET" and path.startswith("/releases/tags/"):
            wanted = path.rsplit("/", 1)[1]
            found = [r for r in FakeGitea.releases.values() if r["tag_name"] == wanted]
            return self.reply(200, found[0]) if found else self.reply(404, {"message": "release not found"})
        if self.command == "POST" and path == "/releases":
            data = json.loads(body)
            release = {**data, "id": FakeGitea.next_id, "assets": []}
            FakeGitea.releases[release["id"]] = release
            FakeGitea.next_id += 1
            return self.reply(201, release)
        match = re.fullmatch(r"/releases/(\d+)(/assets(?:/(\d+))?)?", path)
        if not match or int(match[1]) not in FakeGitea.releases:
            return self.reply(404, {"message": "not found"})
        release = FakeGitea.releases[int(match[1])]
        if self.command == "PATCH" and not match[2]:
            release.update(json.loads(body))
            return self.reply(200, release)
        if self.command == "DELETE" and match[3]:
            release["assets"] = [a for a in release["assets"] if a["id"] != int(match[3])]
            return self.reply(204)
        if self.command == "POST" and match[2] and not match[3]:
            boundary = self.headers["Content-Type"].split("boundary=")[1].encode()
            part = body.split(b"--" + boundary)[1]
            head, content = part.split(b"\r\n\r\n", 1)
            name = self.path.split("name=")[1]
            sent_type = re.search(rb"Content-Type: (\S+)", head)[1].decode()
            asset = {"id": FakeGitea.next_id, "name": name}
            FakeGitea.uploads[asset["id"]] = (content[:-2], b'name="attachment"' in head, sent_type)
            FakeGitea.next_id += 1
            release["assets"].append(asset)
            return self.reply(201, asset)
        return self.reply(405, {"message": "method not allowed"})

    do_GET = do_POST = do_PATCH = do_DELETE = handle_any


server = ThreadingHTTPServer(("127.0.0.1", 0), FakeGitea)
threading.Thread(target=server.serve_forever, daemon=True).start()
gitea_url = f"http://127.0.0.1:{server.server_port}"
sums = sums_for(good)
env = {**os.environ, "GITHUB_SERVER_URL": gitea_url, "GITHUB_REPOSITORY": "owner/lena", "RELEASE_TOKEN": "secret"}
env.pop("GITEA_INTERNAL_URL", None)  # a runner's own setting mustn't leak into the tests


def run_publish(title, extra_env=None, files=(good, sums)):
    """Run the script as the Gitea workflow does, for v0.6.99 on commit abc123."""
    return subprocess.run([sys.executable, str(ROOT / "scripts/publish_gitea_release.py"), "v0.6.99", "abc123", title,
                           str(tmp / "notes.md"), "false", *map(str, files)], env={**env, **(extra_env or {})},
                          capture_output=True, text=True)


result = run_publish("v0.6.99")
release = next(iter(FakeGitea.releases.values()), {})
check(result.returncode == 0 and release.get("tag_name") == "v0.6.99" and release.get("target_commitish") == "abc123"
      and release.get("name") == "v0.6.99" and release.get("body") == notes and release.get("prerelease") is False
      and release.get("draft") is False,
      f"new release: created with its tag on the released commit, title and notes ({result.stdout.strip()!r} {result.stderr.strip()!r})")
assets = {a["name"]: FakeGitea.uploads[a["id"]] for a in release.get("assets", [])}
check(sorted(assets) == sorted([good.name, "SHA256SUMS"]), f"…with the APK and SHA256SUMS attached ({sorted(assets)})")
check(assets.get(good.name) == (good.read_bytes(), True, "application/vnd.android.package-archive")
      and assets.get("SHA256SUMS", (None,))[0] == sums.read_bytes(),
      "…uploaded byte for byte, as Gitea's \"attachment\" field, the APK as an Android package")
check(all(auth == "token secret" for _, _, auth in FakeGitea.calls), "every call carries the token")

result = run_publish("v0.6.99 again")
check(result.returncode == 0 and len(FakeGitea.releases) == 1 and release["name"] == "v0.6.99 again"
      and sorted(a["name"] for a in release["assets"]) == sorted([good.name, "SHA256SUMS"]),
      "run again for the same tag: the release is updated and its files replaced, not doubled")

result = run_publish("v0.6.99", {"RELEASE_TOKEN": "wrong"})
check(result.returncode != 0 and "401" in result.stderr, f"a token Gitea refuses: stops with Gitea's answer ({result.stderr.strip()[:80]})")
result = run_publish("v0.6.99 inside", {"GITHUB_SERVER_URL": "https://gitea.example.invalid", "GITEA_INTERNAL_URL": gitea_url})
check(result.returncode == 0 and release["name"] == "v0.6.99 inside",
      "public address unreachable from the runner: GITEA_INTERNAL_URL is used instead")
result = run_publish("v0.6.99", {"RELEASE_TOKEN": ""})
check(result.returncode != 0 and "RELEASE_TOKEN" in result.stderr, "no token: stops and says which setting is missing")
calls_before = len(FakeGitea.calls)
result = run_publish("v0.6.99", files=(tmp / "missing.apk",))
check(result.returncode != 0 and "missing.apk" in result.stderr and len(FakeGitea.calls) == calls_before,
      "a file that wasn't built: stops before touching the release")
server.shutdown()

# --- missing_releases.py: which versions GitHub's catch-up starts a release for ----------------------------------

LOG = "\n".join(f"## [{v}] - 2026-11-01\n\n- something\n" for v in ("0.6.17", "0.6.16", "0.6.15", "0.6.14", "0.6.13", "0.6.2-beta"))
TAGS = ["v0.6.2-beta", "v2.18.1", "v0.6.13", "v0.6.14", "v0.6.15", "v0.6.16", "v0.6.17", "v0.6.99"]


def starts(releases, runs=(), tags=TAGS, retry=False):
    """The tags the catch-up would start, and its warnings."""
    actions = missing_releases.to_release(tags, [{"tagName": t, "isDraft": False} for t in releases], list(runs), LOG, retry)
    return [t for k, t in actions if k == "release"], [t for k, t in actions if k == "warning"]


got, _ = starts(["v0.6.15"])
check(got == ["v0.6.16", "v0.6.17"],
      f"versions newer than the newest released, oldest first; not v2.x, betas or a tag without a changelog section ({got})")
got, _ = starts(["v0.6.13", "v0.6.17"])
check(got == [], f"an older version left without a release isn't started ({got})")
got, _ = starts([])
check(got == ["v0.6.17"], f"no release on GitHub at all: only the newest version ({got})")
got, _ = starts(["v0.6.15"], runs=[{"headBranch": "v0.6.16", "status": "in_progress", "conclusion": None}])
check(got == ["v0.6.17"], f"a version whose release is already running is left to that run ({got})")
got, warned = starts(["v0.6.15"], runs=[{"headBranch": "v0.6.16", "status": "completed", "conclusion": "failure"}])
check(got == ["v0.6.17"] and len(warned) == 1 and "v0.6.16" in warned[0],
      f"a failed release run isn't started again, it's reported ({got}, {warned})")
got, _ = starts(["v0.6.15"], runs=[{"headBranch": "main", "displayTitle": "Release 0.6.16", "status": "queued", "conclusion": None}])
check(got == ["v0.6.17"], f"a run started from main is matched by its title, Release 0.6.16 ({got})")
got, warned = starts(["v0.6.15"], runs=[{"headBranch": "v0.6.16", "status": "completed", "conclusion": "failure"}], retry=True)
check(got == ["v0.6.16", "v0.6.17"] and not warned, f"asked to start failed ones again: it does ({got}, {warned})")
got, _ = starts(["v0.6.15"], runs=[{"headBranch": "v0.6.16", "status": "completed", "conclusion": "failure"},
                                   {"headBranch": "main", "displayTitle": "Release v0.6.16", "status": "in_progress", "conclusion": None}],
                retry=True)
check(got == ["v0.6.17"], f"…but not one that's running again already ({got})")
actions = missing_releases.to_release(TAGS, [{"tagName": "v0.6.13", "isDraft": False}, {"tagName": "v0.6.14", "isDraft": True}], [], LOG)
check([t for k, t in actions if k == "release"] == ["v0.6.14", "v0.6.15", "v0.6.16"]
      and any("v0.6.17" in t for k, t in actions if k == "warning"),
      f"a draft counts as missing, and at most three start at once, the rest next time ({actions})")
releases = [{"tagName": t, "isDraft": False} for t in ("v0.6.15", "v0.6.17", "v2.18.1")]
check(missing_releases.is_latest("v0.6.17", releases, LOG) and not missing_releases.is_latest("v0.6.16", releases, LOG),
      "Latest only for the newest of Lena's versions released, whichever finishes last (a v2.x release doesn't count)")
check(not missing_releases.is_latest("v0.6.16", releases + [{"tagName": "v0.6.16", "isDraft": False}], LOG),
      "…an older release published after a newer one isn't Latest")

print(f"\n{fails} failures")
sys.exit(1 if fails else 0)
