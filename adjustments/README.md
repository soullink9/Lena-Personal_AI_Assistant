# Adjustments

Requests for changes, kept in the repository so each one is read and answered before the next build and release.

## Asking for one

1. Copy [TEMPLATE.md](TEMPLATE.md) into this folder with a short name, such as `2026-11-02-bigger-clock.md`. On GitHub: **Add file → Upload files** (upload your filled-in copy), or **Add file → Create new file** and paste the template in.
2. Fill it in and leave **Status: New**.
3. Commit it.

## What happens to it

| Status | Meaning |
|---|---|
| **New** | Not read yet. A release won't publish while any adjustment is New. |
| **Reviewed** | Read and accepted, with a note under Review on how and when. |
| **Declined** | Read and not done, with the reason under Review. Moves to `done/`. |
| **Done** | Built, with the version it shipped in. Moves to `done/`. |

Before every build, open adjustments are reviewed (`python3 tools/check.py` fails while any is New, and the build's summary lists them). Before every release, the release workflow stops if any is still New.
