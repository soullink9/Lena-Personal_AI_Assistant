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
│   ├── Modelfile                  Lena's character and model settings
│   └── docker-compose.example.yml Ollama + keep-alive watchdog
├── home-assistant/
│   ├── lena_prompt.txt            prompt pasted into the Ollama agent
│   ├── templates/
│   │   └── lena_memory_sensor.yaml      memory sensor (YAML)
│   └── blueprints/automation/soullink9/
│       └── ai_persona_engine.yaml       mood and energy blueprint
├── android/                       the Lena app (Gradle project)
│   └── app/src/main/kotlin/io/github/soullink9/lena/
├── voice/
│   ├── piper/docker-compose.example.yml text-to-speech example
│   └── bridge/                    Wyoming bridge for Open WebUI
├── tools/
│   └── check.py                   offline checks
├── docs/                          everything in detail
│   ├── Overview.md, Development.md
│   ├── home-assistant/, app/, voice/
└── .github/
    ├── workflows/build.yml        builds, signs and releases the app
    └── ISSUE_TEMPLATE/
```

The blueprint sits in `blueprints/automation/soullink9/` on purpose. That's the folder Home Assistant expects (`config/blueprints/automation/<author>/`), so the whole `blueprints` folder can be copied into a Home Assistant configuration as it is.

The app build only runs when something in `android/` or the workflow changes, so editing docs or Home Assistant files doesn't build an APK.

## Things that must stay in step

| If you change... | ...also update |
|---|---|
| Her character in `ollama/Modelfile` | The "Who you are" section of `home-assistant/lena_prompt.txt` |
| A word used in the blueprint pools | The legend in `lena_prompt.txt` and the options on the [Helpers](home-assistant/Helpers.md) page |
| An entity ID | `lena_prompt.txt`, `lena_memory_sensor.yaml`, and the pages that mention it |
| `num_ctx` in the Modelfile | Home Assistant's context window advice in [Installation](home-assistant/Installation.md) and [Performance tuning](home-assistant/Performance-Tuning.md), and the default of **Lena's context size** in `android/.../Prefs.kt` |
| The Docker network name | `ollama/docker-compose.example.yml`, `voice/bridge/compose.yaml` and the [Voice bridge](voice/Voice-Bridge.md) page |
| The app's version | `versionCode` and `versionName` in `android/app/build.gradle.kts`, plus an **App** entry in `CHANGELOG.md` |
| An Open WebUI endpoint the app uses | `Api.kt`, and the endpoint list in [App development](app/Development.md) |

## Running the checks

```
pip install pyyaml jinja2
python3 tools/check.py
```

The script checks that the YAML files and templates parse, that the prompt renders with every helper missing, that every persona word in the blueprint appears in the prompt, that the compose watchdog escapes its dollar signs, that the context size matches across the Modelfile and the app, that the voice bridge compiles, and that no obvious private data (home IP addresses, tokens, passwords) has crept in. It can't test Home Assistant, Ollama or the app itself.

## Testing a change for real

- **Prompt:** paste it into **Developer tools → Template**, then ask Lena a command, a question, a joke and a serious question.
- **Blueprint:** reload automations, change a helper by hand, and read the automation trace.
- **Modelfile:** `ollama create lena -f ollama/Modelfile`, then `ollama run lena`.
- **Memory sensor:** run the configuration check, reload template entities, and look at the sensor in **Developer tools → States**.
- **App:** push to `main` (or run **Build APK** by hand) and install the APK from the run. See [App development](app/Development.md).
- **Voice bridge:** rebuild the container, then try call mode in Open WebUI and check the bridge's log.

## Releasing

- **The app** is released from GitHub Actions: bump the version, add the changelog entry, then **Actions → Build APK → Run workflow** with **Publish a release** ticked. Details in [App development](app/Development.md#making-a-release).
- **The Home Assistant files and the Modelfile** don't need a build. Add an entry under **Home Assistant personality** in `CHANGELOG.md`, run the checks and commit. People update by copying the new files in (or re-importing the blueprint).

## Conventions

- Every file should make sense to someone seeing it for the first time. Write comments that explain why.
- Home Assistant helpers are created through the UI, never in YAML.
- The app targets Android 16 only (`minSdk`, `targetSdk` and `compileSdk` 36).
- Keep personal details out: names, addresses, IP addresses, tokens, and the contents of a real memory list. Use `Alex` and `Sam` in examples.
- Docs live in `docs/` and are reviewed with the code. The README stays short and links here.
