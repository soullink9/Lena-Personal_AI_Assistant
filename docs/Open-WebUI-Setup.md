# Open WebUI Setup

[← Back to README](../README.md)

Lena shows your real Open WebUI, so almost everything works as it does in a browser. A few server settings matter on a phone.

## HTTPS

Lena only connects over `https://`. Android also refuses microphone access to plain `http://` pages, so call mode can't work without it.

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

If either is set to "Web API", call mode will open but either won't hear you or won't answer out loud.

## Picking a model

The **model ID** in Lena's settings decides which model new chats from the assistant gesture and the Call shortcut use. Leave it blank to use Open WebUI's default.

The ID is the model's identifier in Open WebUI, not necessarily its display name. For a workspace model, open **Workspace → Models**, edit the model, and use the ID shown there (for example, `lena`).

Tools, knowledge and system prompts attached to that model in Open WebUI apply as usual, because Lena is the same Open WebUI page you use on a computer.

## Sign-in

Sign in with your Open WebUI username and password (or LDAP). Lena keeps you signed in.

Sign-in that hops to another website, such as SSO through Authentik, Authelia, Google or GitHub, doesn't work. Lena only opens your Open WebUI address, so the sign-in page opens in your normal browser instead and can't hand the session back.

## Open WebUI version

Lena uses Open WebUI's `call` and `model` web address options, which have been part of Open WebUI since 2024. Any current version works.
