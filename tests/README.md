# Tests

Lena's tests live next to what they test; this folder holds the release scripts' tests. Run everything from the
repository root. Each suite prints `PASS` or `FAIL` per check and exits non-zero if anything failed.

| Command | What it checks |
|---|---|
| `python3 tools/check.py` | Offline, in seconds: versions, feelings, defaults, private data, approved dependencies, and `tests/test_release.py` |
| `python3 tests/test_release.py` | The release scripts in `scripts/`, including publishing to a fake Gitea server |
| `scripts/test_home_assistant.sh latest` (or `2026.9`) | The integration in a real Home Assistant (`home-assistant/tests`); it pip installs, so use a virtualenv |
| `scripts/start_home_assistant.sh` | The newest Home Assistant on PyPI starting with Lena |
| `tools/commands_check/run.sh` | What she understands on her own (timers, alarms, "stop") on example sentences |
| `tools/pairing_check/run.sh` | Set up from your phone: Lena's QR codes against ones a QR reader read back, the page the device serves used as a phone would, and the page's cipher (in Node, when it's installed) against Java's |
| `tools/camera_check/run.sh` | Camera mode: the cameras and sensors it finds in UniFi Protect's entities, and what each view shows as motion comes and goes |
| `tools/frontend_check/run.sh` | The app's wake word front end against the original C, bit for bit |

`test_release.py` covers:

- `release_notes.py`: the tag must match the app, the integration, both Open WebUI files and the voice bridge;
  versions with a suffix are refused; the changelog section is matched on the whole version; the notes start
  with install steps and every link in them is absolute, pointing at GitHub at the tag.
- `check_apk.py`, on small APK-shaped files it makes: an APK signed with an APK signature and naming Lena's package
  and version passes; one that's unsigned, debug-signed, of another version or app, missing its code, wrongly
  named, or missing from (or wrong in) `SHA256SUMS` is refused.
- `publish_gitea_release.py`, against a fake Gitea server on `127.0.0.1`: a new release with its tag on the
  released commit, both files uploaded byte for byte; running it again updates the release and replaces its
  files rather than doubling them; a refused token, `GITEA_INTERNAL_URL`, a missing token and a missing file.
- `missing_releases.py`, on made-up tags, releases and runs: only Lena's versions newer than the newest released
  are started, oldest first and at most three at once (no betas, v2.x tags or tags without a changelog section);
  with no releases only the newest; one already running (matched by its branch or its title) is left to its run,
  and one that failed is reported, or started again when asked; a
  draft counts as missing; and only the newest of Lena's versions is marked Latest.

What this does and does not prove:

- It exercises the scripts' own code paths, the way both release workflows call them.
- It does **not** build a real APK or talk to a real Gitea, so it can't catch a change in Android's APK format or
  Gitea's API. The first release after changing them is the real test; it's safe to run again for the same tag.
