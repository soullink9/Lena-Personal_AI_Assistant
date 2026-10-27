# Disclosure

This project was primarily created for personal use and for my family. While I may provide updates, they are not guaranteed. However, if you've stumbled upon this and find it interesting, feel free to download and use it. I had a great time building this and hope you enjoy it.

# Lena: an AI personality for Home Assistant

Give your voice assistant a character. Lena is a sharp, dryly funny, quietly caring personality that runs on your own hardware, controls your home through Home Assistant, remembers things you tell her, and has moods that drift through the day.

[![Open your Home Assistant instance and show the blueprint import dialog with a specific blueprint pre-filled.](https://my.home-assistant.io/badges/blueprint_import.svg)](https://my.home-assistant.io/redirect/blueprint_import/?blueprint_url=https%3A%2F%2Fgithub.com%2Fsoullink9%2FHomeAssistant-AI_Personality%2Fblob%2Fmain%2Fhome_assistant%2Fblueprints%2Fautomation%2Fsoullink9%2Fai_persona_engine.yaml)

- **A portable personality.** An Ollama `Modelfile` carries her character, so she is the same Lena in Open WebUI, Home Assistant, or the command line.
- **A voice-first Home Assistant prompt.** Short spoken replies, honest reporting of what actually happened, and a real assistant side beyond switching lights.
- **Moods that change.** A blueprint drifts her mood, energy, delivery and flavor through the day, and the prompt makes the difference audible.
- **A memory.** She saves facts to a Home Assistant to-do list when you ask her to remember something.
- **Everything runs locally.** Ollama for the model, Piper for the voice. Examples for keeping the model loaded and tuning the voice are included.

## Quick start

You need Home Assistant (2024.10 or newer), a machine running [Ollama](https://ollama.com/) with a GPU that fits a 9B-class model, and Home Assistant's Ollama integration set up with Assist.

1. **Build the model.** From the `ollama` folder: `ollama create lena -f Modelfile`.
2. **Create the helpers.** Five dropdown helpers plus a to-do list, all through the Home Assistant UI. Names and options are on the [Helpers](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Helpers) wiki page.
3. **Import the blueprint** with the button above and create one automation from it.
4. **Add the prompt.** Paste `home_assistant/lena_prompt.txt` into the Ollama agent's *Instructions* and adjust the entity IDs.
5. **Optional: memory.** Add the sensor in `home_assistant/templates/` so she can read what she has saved.

Step-by-step instructions are on the [Installation](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Installation) wiki page.

## Documentation

All details live in the [wiki](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki).

| Page | What's in it |
| --- | --- |
| [Installation](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Installation) | Full setup from an empty Ollama to a talking Lena |
| [Helpers](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Helpers) | The five dropdown helpers and the exact option names |
| [Prompt-Reference](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Prompt-Reference) | How the Modelfile and the Home Assistant prompt split the work, and how to edit them |
| [Persona-Engine](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Persona-Engine) | How the blueprint rolls mood, energy, delivery and flavor |
| [Memory](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Memory) | The to-do list memory and its sensor |
| [Voice-Piper](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Voice-Piper) | Choosing and tuning a Piper voice |
| [Performance-Tuning](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Performance-Tuning) | Context size, thinking mode, keeping the model loaded |
| [Troubleshooting](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Troubleshooting) | Errors seen while building this, and their fixes |
| [Development](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Development) | How the repo is organized and how to change things safely |

## Repository layout

| Path | Purpose |
| --- | --- |
| `ollama/Modelfile` | Lena's character and model settings |
| `ollama/docker-compose.example.yml` | Ollama plus a keep-alive watchdog (example) |
| `home_assistant/lena_prompt.txt` | The prompt pasted into Home Assistant |
| `home_assistant/blueprints/automation/soullink9/ai_persona_engine.yaml` | The mood and energy blueprint |
| `home_assistant/templates/lena_memory_sensor.yaml` | Sensor that exposes her memory to the prompt |
| `piper/docker-compose.example.yml` | Piper text-to-speech example |
| `tools/check.py` | Offline sanity checks to run before committing |
| `wiki/` | Source for the GitHub wiki pages |
| `.github/` | Bug report template |
| `CHANGELOG.md` | Version history and known issues |

## Privacy

Nothing in this repository sends data to any service. The prompt goes to your own Ollama server, and her memory is a to-do list inside your Home Assistant. The memory text is added to every prompt, so do not store passwords, door codes or similar secrets in it. More on the [Memory](https://github.com/soullink9/HomeAssistant-AI_Personality/wiki/Memory) wiki page.

## Contributing

Issues and suggestions are welcome, with the caveat from the disclosure above: updates are not guaranteed. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

No code in this repository was copied from another project. It builds on, and would not exist without:

- [Home Assistant](https://www.home-assistant.io/) and its Assist, Ollama, Wyoming and blueprint features.
- [Ollama](https://ollama.com/) for running the model locally. The base model named in the Modelfile is a Qwen model from Alibaba Cloud; other choices are listed in the file.
- [Piper](https://docs.linuxserver.io/images/docker-piper/) text-to-speech, packaged as a container by [LinuxServer.io](https://www.linuxserver.io/).
- [Open WebUI](https://openwebui.com/), where the same model is used for typed chat.
- [curl](https://curl.se/), used by the watchdog example.
- The idea of a Jarvis-style assistant comes from the J.A.R.V.I.S. of Marvel's *Iron Man*. Lena is an original character and this project is not affiliated with Marvel.

## License

[GPL-3.0](LICENSE). You may use, change and share this project, and anything you distribute that is based on it must stay under the same license.
