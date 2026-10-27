# Lena for Home Assistant

Lena is an AI personality that runs on your own hardware. She controls your home through Home Assistant, speaks through Piper, remembers things in a to-do list, and has moods that change through the day.

> This project was made mainly for personal and family use. Updates are not guaranteed. See the disclosure at the top of the [README](https://github.com/soullink9/HomeAssistant-AI_Personality#disclosure).

## How the pieces fit together

```
You speak -> Whisper (speech to text) -> Home Assistant Assist
                                              |
                  prompt (lena_prompt.txt) + live home data + Lena's memory + today's mood
                                              |
                                  Ollama model "lena" (Modelfile = her character)
                                              |
                               device actions  +  reply text
                                              |
                                     Piper (text to speech) -> speaker
```

Two files define her. The **Modelfile** says who she is and travels with the model everywhere. The **Home Assistant prompt** says where she is right now: spoken replies only, the state of the house, what she remembers, and her current mood. The **blueprint** changes that mood over time.

## Pages

- [Installation](Installation)
- [Helpers](Helpers)
- [Prompt-Reference](Prompt-Reference)
- [Persona-Engine](Persona-Engine)
- [Memory](Memory)
- [Voice-Piper](Voice-Piper)
- [Performance-Tuning](Performance-Tuning)
- [Troubleshooting](Troubleshooting)
- [Development](Development)

## Status

Version 1.0.0. See the [changelog](https://github.com/soullink9/HomeAssistant-AI_Personality/blob/main/CHANGELOG.md) for known issues.
