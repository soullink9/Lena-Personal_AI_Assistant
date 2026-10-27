# Development

How the repository is organized and how to change it without breaking things.

## Layout

```
.
├── README.md                  short front page, disclosure first
├── CHANGELOG.md               versions and known issues
├── CONTRIBUTING.md
├── LICENSE                    GPL-3.0
├── ollama/
│   ├── Modelfile              Lena's character and model settings
│   └── docker-compose.example.yml   Ollama + keep-alive watchdog
├── home_assistant/
│   ├── lena_prompt.txt        prompt pasted into the Ollama agent
│   ├── templates/
│   │   └── lena_memory_sensor.yaml  memory sensor (YAML)
│   └── blueprints/automation/soullink9/
│       └── ai_persona_engine.yaml   mood and energy blueprint
├── piper/
│   └── docker-compose.example.yml   text-to-speech example
├── tools/
│   └── check.py               offline sanity checks
└── wiki/                      source for the GitHub wiki
```

The blueprint sits in `blueprints/automation/soullink9/` on purpose. That is the folder Home Assistant expects (`config/blueprints/automation/<author>/`), so the whole `blueprints` folder can be copied into a Home Assistant configuration as it is.

## Things that must stay in step

| If you change... | ...also update |
| --- | --- |
| Her character in `ollama/Modelfile` | The "Who you are" section of `lena_prompt.txt` |
| A word used in the blueprint pools | The legend in `lena_prompt.txt`, the options in the [Helpers](Helpers) page |
| An entity ID | `lena_prompt.txt`, `lena_memory_sensor.yaml`, the wiki pages that mention it |
| `num_ctx` in the Modelfile | The context window advice in [Installation](Installation) and [Performance-Tuning](Performance-Tuning) |

## Running the checks

```
pip install pyyaml jinja2
python3 tools/check.py
```

The script verifies that the YAML files and templates parse, that the prompt renders with every helper missing, that every persona word in the blueprint appears in the prompt, that the compose watchdog escapes its dollar signs, and that no obvious private data is present. It cannot test Home Assistant or Ollama themselves.

## Testing a change for real

- **Prompt:** paste it into Developer tools > Template, then ask Lena a command, a question, a joke and a serious question.
- **Blueprint:** reload automations, change a helper by hand, and read the automation trace.
- **Modelfile:** `ollama create lena -f ollama/Modelfile`, then `ollama run lena`.
- **Memory sensor:** run the configuration check, reload template entities, and inspect the sensor in Developer tools > States.

## Conventions

- Every file should be understandable to someone seeing it for the first time. Write comments that explain why.
- Keep personal details out: names, addresses, tokens, and the contents of a real memory list. Use `Alex` and `Sam` in examples.
- Helpers are created through the Home Assistant UI, never in YAML.

## Releasing

1. Update `CHANGELOG.md`, including known issues.
2. Run `python3 tools/check.py`.
3. Commit, tag (`git tag v1.0.1`) and push the tag.
4. Publish the files in `wiki/` to the GitHub wiki (see below).

## Publishing the wiki

GitHub keeps a wiki in a separate repository. After enabling the wiki on the repository and creating a first page:

```
git clone https://github.com/soullink9/HomeAssistant-AI_Personality.wiki.git
cp wiki/*.md HomeAssistant-AI_Personality.wiki/
cd HomeAssistant-AI_Personality.wiki
git add . && git commit -m "Update wiki" && git push
```

Keep `wiki/` in the main repository as the source of truth so changes are reviewed with the code.
