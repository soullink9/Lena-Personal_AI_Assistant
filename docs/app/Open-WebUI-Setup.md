# Open WebUI Setup

[← Back to README](../../README.md)

Lena shows your real Open WebUI, so almost everything works as it does in a browser. A few server settings matter on a phone.

## HTTPS

Lena only connects over `https://`. Android also refuses microphone access to plain `http://` pages, so Open WebUI's voice input can't work without it.

Any of these work:

- **A reverse proxy** (Caddy, Nginx Proxy Manager, Traefik and so on) with a public certificate, such as Let's Encrypt.
- **Tailscale** with HTTPS certificates turned on, using your machine's `.ts.net` name.
- **Your own certificate authority.** Install its certificate on the phone first: **Settings → Security → Encryption & credentials → Install a certificate → CA certificate** (the exact menu varies by phone). Lena trusts certificates you install yourself; see [Privacy and Security](Privacy-and-Security.md#certificates) if you'd rather it didn't.

Self-signed certificates that aren't from an installed authority are rejected.

## Speech engines

The in-app browser has no speech engine of its own, so speech must run on your server.

In Open WebUI: **Admin Panel → Settings → Audio**.

| Setting | Use |
|---|---|
| Speech-to-Text engine | A server engine, such as Whisper (local) or an OpenAI-compatible endpoint. **Not** "Web API". |
| Text-to-Speech engine | A server engine, such as your own TTS service or an OpenAI-compatible endpoint. **Not** "Web API". |

If either is set to "Web API", talking to Lena with **Your server** or **Your server's voice** either won't hear you or won't answer out loud.

Talking to Lena in the sheet uses the same text-to-speech for her voice, and the same speech-to-text if you set **Hearing you** to **Your server**.

Open WebUI has a built-in Whisper for speech-to-text (**Whisper (Local)**), but no good local voice. If Home Assistant already has Piper and Whisper containers, the [Voice Bridge](../voice/Voice-Bridge.md) lets Open WebUI use them, so Lena sounds the same everywhere.

## Picking a model

**Settings → Model** decides which model new chats, the Ask sheet and scheduled prompts use. Pick it from the list (it fills in once you're signed in). **Lena, automatically** uses the model built from the Modelfile (`lena`), or one named Lena. If your server has neither, new chats use Open WebUI's default and the Ask sheet and scheduled prompts use the first model on your list.

You can still start a chat with any model by tapping the title in the top bar.

To talk to Lena herself, pick the `lena` model. Her personality, feelings and shared memory then work in the app exactly as in Open WebUI, because the app goes through Open WebUI: set them up once in [Lena in Open WebUI](../open-webui/Setup.md).

## Tools

**In the main app**, chats are the real Open WebUI page, so tools work exactly as they do in your browser.

**In the Ask sheet and scheduled prompts**, Lena starts a real Open WebUI chat through its API and Open WebUI answers it on your server, with the full tool loop. Lena turns on:

- every tool and MCP server **attached to the model** in **Workspace → Models** (its Tools list),
- the model's **default features** (web search, code interpreter, image generation) if you've set them on the model.

So the easiest way to make a tool available everywhere is to attach it to your model in Open WebUI. Open WebUI's memory feature follows your own memory setting, as on the web page. Follow-up suggestions and tags are turned off for these chats.

Open WebUI's own built-in tools (its knowledge, notes, time and so on) are offered when the model is set to **Native** function calling, as in the browser.

## Sign-in

Lena keeps you signed in either way.

- **Username and password (or LDAP):** nothing to set up.
- **Single sign-on** (Authentik, Authelia, Keycloak and so on), whether through Open WebUI's own sign-in button or a proxy in front of it: add your sign-in page's address in Lena's settings under **Sign-in address**, for example `https://auth.example.com`. Without it, Lena sends the sign-in page to your normal browser, which can't hand the session back.

Sign-in addresses can load inside Lena, but they never get the microphone. If your sign-in hops through more than one site (for example, Authentik passing you on to Google), add each address, separated by spaces.

Passkeys and security keys may not work inside Lena, because Android's web view only supports them in apps built with extra Google libraries. Use a password with a one-time code or a push approval for this sign-in.

## Open WebUI version

Lena uses Open WebUI's `model` and `temporary-chat` web address options, its folders and its notes, all part of current Open WebUI. Any current version works.
