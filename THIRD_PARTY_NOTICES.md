# Third-party notices

Lena's own code is licensed under the [GNU General Public License v3.0](LICENSE). This repository and the app built from it also include, or are built with, the following third-party material, each under its own licence.

## Included in the repository and the app

| What | Where | Source | Licence |
|---|---|---|---|
| "Hey Lena" wake word model | `android/app/src/main/assets/wakeword/hey_lena.*` | [TaterTotterson/Tater-Wake-Words](https://github.com/TaterTotterson/Tater-Wake-Words) | MIT (copy in `assets/wakeword/licenses/hey_lena-LICENSE.txt`) |
| "Lena" wake word model | `android/app/src/main/assets/wakeword/lena.*` | [TaterTotterson/microWakeWords](https://github.com/TaterTotterson/microWakeWords) | MIT (copy in `assets/wakeword/licenses/hey_lena-LICENSE.txt`) |
| Voice activity model | `android/app/src/main/assets/wakeword/vad.*` | [esphome/micro-wake-word-models](https://github.com/esphome/micro-wake-word-models), Open Home Foundation | Apache 2.0 (licence and NOTICE in `assets/wakeword/licenses/`) |
| "Hey Jarvis" wake word model and openWakeWord's feature models | `android/app/src/main/assets/wakeword/hey_jarvis_v0.1.tflite`, `melspectrogram.tflite`, `embedding_model.tflite` | [dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) v0.5.1 | Code Apache 2.0; the Hey Jarvis model **CC BY-NC-SA 4.0** (non-commercial) |
| TensorFlow Lite Micro audio front end | Ported in `android/app/src/main/java/.../MicroFrontend.java`; original C in `tools/frontend_check/vendor/` | [tensorflow/tflite-micro](https://github.com/tensorflow/tflite-micro) | Apache 2.0 (also shipped in the app as `assets/wakeword/licenses/tflite-micro-LICENSE.txt`) |
| KISS FFT (fixed point) | Ported in `MicroFrontend.java`; original C in `tools/frontend_check/vendor/` | [mborgerding/kissfft](https://github.com/mborgerding/kissfft) | BSD (shipped in the app as `assets/wakeword/licenses/kissfft-LICENSE.txt`) |
| Material Icons glyphs | `android/app/src/main/res/drawable/ic_glyph_*`, `ic_shortcut_*`, and the Apps and Settings icons in `android/app/src/main/assets/js/home_assistant.js` | [google/material-design-icons](https://github.com/google/material-design-icons) | Apache 2.0 |

## Used by the app at build time (downloaded by Gradle, not stored here)

Google's Android Jetpack libraries (Compose, Material 3, WebKit, WorkManager, Glance), JetBrains' kotlinx.coroutines and Google's LiteRT, all Apache 2.0.

## Used alongside Lena (not included)

Ollama (MIT), the Gemma model (Gemma Terms of Use) or whichever model you choose, Open WebUI (its own licence), Home Assistant (Apache 2.0), Piper (MIT), Whisper (MIT), FFmpeg (LGPL/GPL), and the LinuxServer.io container images. Their licences apply to your use of them.

## Notes

- **"Hey Jarvis" is for non-commercial use only** (CC BY-NC-SA 4.0). Anyone distributing Lena commercially must remove it.
- J.A.R.V.I.S. is a character from Marvel's *Iron Man*; Lena is an original character, and this project isn't affiliated with Marvel, Google, Open WebUI, Home Assistant or Ollama.
