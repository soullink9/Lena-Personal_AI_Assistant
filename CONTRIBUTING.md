# Contributing

This is a personal project shared in case it helps someone, so replies and merges may be slow. Issues and pull requests are still welcome.

## Reporting a problem

Open an issue and include:

- Home Assistant version, Ollama version, the model you used and your GPU.
- What you said or did, what you expected, and what happened.
- For prompt problems, the rendered prompt: Settings > Voice assistants > your assistant > Debug shows what was sent.
- Relevant lines from the Home Assistant log, with anything private removed.

## Changing the code

- Read the [Development](wiki/Development.md) page first. It explains the layout and the checks to run.
- Keep comments in every file readable by someone new to the project. Explain why, not just what.
- Keep the personality text in sync: the character in `ollama/Modelfile` and the short copy in `home_assistant/lena_prompt.txt` should agree.
- If you add a mood, energy, delivery, flavor or reaction value, add it in three places: the blueprint pool, the legend in the prompt, and the options list on the Helpers wiki page.
- Add an entry to `CHANGELOG.md`.
- Never put private data in the repository: IP addresses, tokens, real names, or the contents of a real memory list.
