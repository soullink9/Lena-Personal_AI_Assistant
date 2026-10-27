# Development

[← Back to the README](../README.md)

How the repository is organised and how to change it without breaking things. The app has its own page with the code map and build details: [App development](app/Development.md).

## Layout

```
.
├── README.md                      short front page, disclosure first
├── CHANGELOG.md                   app and Home Assistant versions, known issues
├── CONTRIBUTING.md
├── LICENSE                        GPL-3.0
├── ollama/
│   ├── Modelfile                  her character (MESSAGE system) and model settings
│   └── docker-compose.example.yml Ollama + keep-alive watchdog
├── home-assistant/
│   ├── lena_prompt.txt            prompt pasted into the Ollama agent
│   ├── custom_templates/
│   │   └── lena.jinja             how each feeling sounds; feelings()
│   └── blueprints/
│       ├── automation/soullink9/lena_mood_engine.yaml   what moves her feelings
│       └── script/soullink9/
│           ├── lena_feel.yaml     her tool: react to how she's treated
│           └── lena_recall.yaml   her tool: read her memory
├── open-webui/
│   ├── lena_filter.py             feelings and memory into chats
│   └── lena_tools.py              remember, forget, feel
├── android/                       the Lena app (Gradle project)
│   └── app/src/main/kotlin/io/github/soullink9/lena/
├── voice/
│   ├── piper/docker-compose.example.yml text-to-speech example
│   └── bridge/                    Wyoming bridge for Open WebUI
├── tools/
│   ├── check.py                   offline checks
│   └── make_images.py             app icon, notification icon and avatars from the logo
├── docs/                          everything in detail
│   ├── assets/                    the logo, avatar and GitHub social preview
│   ├── Getting-Started.md, Overview.md, Development.md
│   ├── personality/, open-webui/, home-assistant/, app/, voice/
└── .github/
    ├── workflows/build.yml        builds, signs and releases the app
    └── ISSUE_TEMPLATE/
```

The blueprints sit in `blueprints/<automation|script>/soullink9/` and the template in `custom_templates/` on purpose. Those are the folders Home Assistant expects, so both folders can be copied into a Home Assistant configuration as they are.

The app build only runs when something in `android/` or the workflow changes, so editing docs or Home Assistant files doesn't build an APK.

## Things that must stay in step

| If you change... | ...also update |
|---|---|
| Her character | Only `ollama/Modelfile`. Nothing else repeats it. Rebuild the model. |
| A feeling's name (adding, renaming) | `lena.jinja`, its dropdown's options on the [Helpers](home-assistant/Helpers.md) page, the mood engine's lists, and for moments she sets herself `lena_feel.yaml` and `open-webui/lena_tools.py`. See [Her feelings](personality/Moods.md#add-or-rename-a-feeling). |
| A feelings helper's entity ID | The defaults in `lena.jinja`'s `feelings()` (or the call in `lena_prompt.txt` and `FEELINGS_TEMPLATE` in `lena_filter.py`), and the [Helpers](home-assistant/Helpers.md) page |
| The memory list's entity ID | The `memory_list` settings of the Open WebUI filter and tool, and the [Helpers](home-assistant/Helpers.md) page |
| `num_ctx` in the Modelfile | Home Assistant's context window advice in [Installation](home-assistant/Installation.md) and [Performance tuning](home-assistant/Performance-Tuning.md), and the default of **Her context size** (Settings → Conversation) in `android/.../Prefs.kt` |
| The Docker network name | `ollama/docker-compose.example.yml`, `voice/bridge/compose.yaml` and the [Voice bridge](voice/Voice-Bridge.md) page |
| The app's version | `versionCode` and `versionName` in `android/app/build.gradle.kts`, plus an **App** entry in `CHANGELOG.md` |
| An Open WebUI endpoint the app uses | `Api.kt`, and the endpoint list in [App development](app/Development.md) |

## Running the checks

```
pip install pyyaml jinja2
python3 tools/check.py
```

The script checks that:

- the YAML files and templates parse;
- `feelings()` renders every combination of mood, energy and moment, describing only the current ones;
- her Home Assistant prompt renders;
- every feeling the mood engine, the Lena feel script and the Open WebUI tool use is defined in `lena.jinja`, and the script and tool offer the same moments;
- every branch of the mood engine runs against stand-in states and writes only defined feelings;
- the Modelfile carries her character in `MESSAGE system`, and its context size matches the app's;
- the Python files compile, the compose watchdog escapes its dollar signs, and no obvious private data (home IP addresses, tokens, passwords) has crept in.

It can't test Home Assistant, Ollama, Open WebUI or the app themselves.

## Testing a change for real

- **Modelfile:** `ollama create lena -f ollama/Modelfile`, then the test in [Getting started](Getting-Started.md#1-her-model), then a command, a question, a joke and a serious question.
- **Feelings:** copy `lena.jinja` in, reload custom templates, set the helpers by hand and render `feelings()` in **Developer tools → Template**.
- **Mood engine and scripts:** reload automations and scripts, change a helper by hand, and read the traces.
- **Prompt:** paste it into **Developer tools → Template**.
- **Open WebUI filter and tool:** paste the new version over the old one in Open WebUI, then try the test in [Lena in Open WebUI](open-webui/Setup.md#7-test-it).
- **App:** push to `main` (or run **Build APK** by hand) and install the APK from the run. See [App development](app/Development.md).
- **Voice bridge:** rebuild the container, then try call mode in Open WebUI and check the bridge's log.

## Releasing

- **The app** is released from GitHub Actions: bump the version, add the changelog entry, then **Actions → Build APK → Run workflow** with **Publish a release** ticked. Details in [App development](app/Development.md#making-a-release).
- **The personality** (Modelfile, Home Assistant files, Open WebUI files) doesn't need a build. Add an entry under **Personality** in `CHANGELOG.md`, run the checks and commit. People update by rebuilding the model, copying the new files in (or re-importing the blueprints) and pasting the Open WebUI files over the old ones.

## The logo

`docs/assets/lena-logo.png` is the source for every image. `python3 tools/make_images.py` (needs `pillow`, `numpy` and `scipy`) rebuilds from it:

| Image | Used for |
|---|---|
| `mipmap-*/ic_launcher_foreground.png` | The app icon and the launch screen, on the logo's dark background (`icon_bg`) |
| `mipmap-*/ic_launcher_monochrome.png` | Android's themed icons: the logo's main shapes as a silhouette |
| `drawable-*/ic_stat_logo.png` | The notification icon |
| `drawable-nodpi/lena_avatar.png` | Her round avatar in the sheet, drawer, settings, widget and notifications |
| `docs/assets/lena-avatar.png` | The `lena` model's profile image in Open WebUI |
| `docs/assets/social-preview.png` | GitHub's social preview: Settings → General → Social preview |

To change the logo, run it with the new file (`python3 tools/make_images.py new-logo.png`), then check the icon in a circle and a rounded square, and the notification icon at small size.

## Conventions

- Every file should make sense to someone seeing it for the first time. Write comments that explain why.
- Home Assistant helpers are created through the UI, never in YAML.
- The app targets Android 16 only (`minSdk`, `targetSdk` and `compileSdk` 36).
- Keep personal details out: names, addresses, IP addresses, tokens, and the contents of a real memory list. Use `Alex` and `Sam` in examples.
- Docs live in `docs/` and are reviewed with the code. The README stays short and links here.
