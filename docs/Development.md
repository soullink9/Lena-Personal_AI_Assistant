# Development

[← Back to the README](../README.md)

How the repository is organised and how to change it without breaking things. The app has its own page with the code map and build details: [App development](app/Development.md).

## Layout

```
.
├── README.md                      short front page, disclosure first
├── ARCHITECTURE.md                how the parts fit together and why
├── CHANGELOG.md                   what changed in each release
├── CONTRIBUTING.md
├── LICENSE                        GPL-3.0
├── THIRD_PARTY_NOTICES.md         included models and code, and their licences
├── .env.example                   the environment variables the project reads
├── ollama/
│   ├── Modelfile                  her character (MESSAGE system) and model settings
│   └── docker-compose.example.yml Ollama + keep-alive watchdog
├── home-assistant/
│   ├── custom_components/lena/    the Lena integration, copied into Home Assistant as it is
│   │   ├── feelings.yaml          every feeling and how it sounds (the only file people edit)
│   │   ├── brain.py               what moves her feelings: plain Python, no Home Assistant
│   │   ├── core.py                runs the brain in Home Assistant, saves feelings and memory
│   │   ├── llm_api.py             the "Lena" choice for conversation agents, and her tools
│   │   ├── view.py                GET /api/lena/context for Open WebUI and the app
│   │   ├── select.py, todo.py     her dropdowns and her memory list
│   │   ├── config_flow.py         adding and configuring her in the UI
│   │   ├── compat.py              differences between Home Assistant versions
│   │   └── brand/                 her icon and logo in Home Assistant (made by make_images.py)
│   ├── tests/                     runs the integration in a real Home Assistant
│   └── pytest.ini
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
    ├── workflows/home-assistant.yml tests the integration on Home Assistant 2026.9 and the latest
    └── ISSUE_TEMPLATE/
```

`custom_components/lena` is laid out the way Home Assistant expects, so it can be copied into a Home Assistant configuration as it is. `feelings.yaml` holds nothing but names and descriptions, so anyone can change how she sounds without reading code.

The app build only runs when something in `android/` or its workflow changes, and the integration tests only when something in `home-assistant/` or theirs does. Editing docs runs neither.

## Things that must stay in step

| If you change... | ...also update |
|---|---|
| Her character | Only `ollama/Modelfile`. Nothing else repeats it. Rebuild the model. |
| A feeling's name (adding, renaming) | `feelings.yaml`; the tables in `brain.py` if her drift uses it; for moments she sets herself, the `Literal` in `open-webui/lena_tools.py`. See [Her feelings](personality/Moods.md#adding-or-removing-one). |
| An entity ID the integration creates | The [integration](home-assistant/Integration.md) and [installation](home-assistant/Installation.md) pages, the `memory_list` setting of the Open WebUI tool, and `DEFAULT_MEMORY_LIST` in the app's `Prefs.kt` |
| What `/api/lena/context` returns | `view.py`, `open-webui/lena_filter.py`, `HomeAssistant.kt` in the app, and the [integration](home-assistant/Integration.md) page |
| How `forget_fact` picks a memory | `best_match` in `brain.py` and `_best_match` in `open-webui/lena_tools.py` |
| `num_ctx` in the Modelfile | Home Assistant's context window advice in [Installation](home-assistant/Installation.md) and [Performance tuning](home-assistant/Performance-Tuning.md), and the default of **Her context size** (Settings → Conversation) in `android/.../Prefs.kt` |
| The Docker network name | `ollama/docker-compose.example.yml`, `voice/bridge/compose.yaml` and the [Voice bridge](voice/Voice-Bridge.md) page |
| The version | One version for the whole project: `versionName` in `android/app/build.gradle.kts` (and a higher `versionCode`), `version` in the integration's `manifest.json`, the `version:` line of both Open WebUI files, and a section in `CHANGELOG.md`. `tools/check.py` checks they match. |
| An Open WebUI endpoint the app uses | `Api.kt`, and the endpoint list in [App development](app/Development.md) |

## Running the checks

```
pip install pyyaml
python3 tools/check.py
```

The script checks that:

- the YAML and JSON files parse, and `strings.json` matches `translations/en.json`;
- `feelings.yaml` has its three sections, every description is text, and every feeling `brain.py` uses is defined;
- each of the brain's events (morning, bedtime, every hour, arrivals, an empty house, weather, worries, fading) chooses only defined feelings, over many random runs;
- her description includes only her current feelings, and forgetting picks the right memory;
- the Open WebUI tool offers exactly the moments she can set herself;
- the Modelfile carries her character in `MESSAGE system`, and its context size matches the app's;
- the Python files compile, the compose watchdog escapes its dollar signs, and no obvious private data (home IP addresses, tokens, passwords) has crept in.

It can't run Home Assistant, Ollama, Open WebUI or the app. The integration's own tests do run Home Assistant: they start it with the integration on GitHub for every change to `home-assistant/`, on 2026.9 and the newest release. To run them yourself: `pip install pytest-homeassistant-custom-component`, then `python -m pytest` in `home-assistant/`.

## Testing a change for real

- **Modelfile:** `ollama create lena -f ollama/Modelfile`, then the test in [Getting started](Getting-Started.md#1-her-model), then a command, a question, a joke and a serious question.
- **Feelings descriptions:** edit `feelings.yaml` in Home Assistant and reload the integration, then set her dropdowns by hand and talk to her. The voice assistant's debug view shows exactly what she was told.
- **The integration:** add a test to `home-assistant/tests`, push, and read the **Home Assistant integration** run. Then copy the folder into a test Home Assistant and restart.
- **Open WebUI filter and tool:** paste the new version over the old one in Open WebUI, then try the test in [Lena in Open WebUI](open-webui/Setup.md#7-test-it).
- **App:** push to `main` (or run **Build APK** by hand) and install the APK from the run. See [App development](app/Development.md).
- **Voice bridge:** rebuild the container, then try call mode in Open WebUI and check the bridge's log.

## Releasing

Everything shares one version and one changelog.

- **Releasing:** set the new version everywhere (see the table above; `tools/check.py` catches a mismatch), add its section to `CHANGELOG.md`, then **Actions → Build APK → Run workflow** with **Publish a release** ticked. That builds the app and publishes the release and its tag. Details in [App development](app/Development.md#making-a-release).
- **The parts without a build** (Modelfile, Home Assistant integration, Open WebUI files) ship in the same release. People update them by rebuilding the model, copying the new integration folder in and restarting, and pasting the Open WebUI files over the old ones; the changelog says which of these a release needs.

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
| `home-assistant/custom_components/lena/brand/` | The integration's icon and logo in Home Assistant 2026.3+: `icon.png` and `@2x` (the logo on its rounded dark tile), `logo.png` with her name in dark text, `dark_logo.png` in white for dark themes |

To change the logo, run it with the new file (`python3 tools/make_images.py new-logo.png`), then check the icon in a circle and a rounded square, the notification icon at small size, and the integration's logo on a light and a dark Home Assistant theme.

## Conventions

- Every file should make sense to someone seeing it for the first time. Write comments that explain why.
- Nothing in Home Assistant is set up in YAML: the integration creates its own entities and is configured in the UI.
- The app targets Android 16 only (`minSdk`, `targetSdk` and `compileSdk` 36).
- Keep personal details out: names, addresses, IP addresses, tokens, and the contents of a real memory list. Use `Alex` and `Sam` in examples.
- Docs live in `docs/` and are reviewed with the code. The README stays short and links here.
