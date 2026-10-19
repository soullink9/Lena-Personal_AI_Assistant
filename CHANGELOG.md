# Changelog

All notable changes are listed here, newest first. Versions follow [Semantic Versioning](https://semver.org/).

## 1.0.0 - 2026-10-18

First public release.

### Included

- **Modelfile** for Lena with a portable personality, 16k context and sampling settings suited to a 16 GB GPU.
- **Home Assistant prompt** with voice rules, accuracy rules, a memory section, household context and a full legend for every mood, energy, delivery, flavor and reaction value.
- **AI Persona Engine blueprint** driving mood, energy, delivery, flavor and an automatic reaction reset. Every value is checked against the helper's real options, and no pool can be empty, so it does not error on missing options.
- **Memory** as a Home Assistant to-do list, with a template sensor that exposes the open items to the prompt.
- **Examples** for Ollama with a keep-alive watchdog, and for Piper text-to-speech.
- **Wiki source** under `wiki/`.

### Known issues

- **Slow replies.** On the author's setup (a 9B-class model fully on an RTX 4070 Ti Super), one short request took about 34 seconds in Home Assistant. Model thinking mode is the leading suspect and has not been ruled out. The checks to run are on the Performance-Tuning wiki page. Treat reply speed as unverified until you test it on your own hardware.
- **Voice-saving memory is untested across models.** Saving a memory depends on the model making a to-do list tool call. Some small models claim to have saved something without calling the tool. Check the list after your first test.
- **The memory sensor is YAML.** Home Assistant's UI template helpers cannot currently call the to-do list action, so this one sensor is defined in a YAML file.
