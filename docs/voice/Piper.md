# Voice (Piper)

Piper turns Lena's text into speech. The example in `piper/docker-compose.example.yml` uses the [LinuxServer.io Piper image](https://docs.linuxserver.io/images/docker-piper/) and connects to Home Assistant through the Wyoming integration (port 10200).

Voice is largely a matter of taste. Change one setting at a time, and judge with real Lena lines: a joke and a plain confirmation.

## Settings

| Variable | What it does |
| --- | --- |
| `PIPER_VOICE` | The voice, written language-name-quality, for example `en_US-lessac-medium` |
| `PIPER_SPEAKER` | Which speaker to use, for voices that contain several |
| `PIPER_LENGTH` | Speaking rate. Below 1.0 is faster, above 1.0 is slower |
| `PIPER_NOISE` | Voice variability. Above 1.0 degrades the audio |
| `PIPER_NOISEW` | Cadence variability. Above 1.0 causes stutters and pauses |
| `LOCAL_ONLY` | If set, only already-downloaded voices are used; nothing is fetched. Download the voice once before turning this on |
| `NO_STREAMING` | If set, turns off sentence-by-sentence streaming. Leave it unset so speech can start before the whole reply is ready |

## Choosing a voice

- **Multi-speaker voices** such as `libritts-high` contain many speakers of very different quality, pitch and gender. Speaker 0 is just the first one. Audition several `PIPER_SPEAKER` numbers.
- **Background hiss** is common on voices trained from audiobook recordings. Try, in this order: a lower `PIPER_NOISE` (for example 0.4) with `PIPER_NOISEW` around 0.3; a different speaker; a voice recorded in a studio, which usually has a single speaker and a cleaner sound.
- **Is it Piper or the speaker?** Generate speech from Home Assistant's text-to-speech test and play it on a phone or laptop. If it is clean there but noisy on your satellite, the satellite's speaker or amplifier is the cause and Piper settings will not help.
- **Latency.** `high` quality voices take longer to synthesize than `medium` ones. If there is a pause before she speaks, try a medium voice to compare.
- **Rate.** Slightly slower than 1.0 can suit a measured delivery, but it can also make dry humor drag.

## Helping the voice from the prompt

Short sentences with ordinary punctuation sound best. Piper treats periods and commas as pauses and can stumble on dashes, parentheses and abbreviations. The prompt already forbids symbols and asks for numbers to be spoken as words. If replies still sound wrong, add a line asking for short sentences and plain punctuation.
