# Lena in Open WebUI

[← Back to Getting started](../Getting-Started.md)

Open WebUI is where you chat with Lena in a browser, and it's what the Lena app talks to. Her character comes from the `lena` model itself. Two small add-ons bring over what the Lena integration in Home Assistant knows: how she feels right now, and her shared memory.

| Piece | File | What it does |
|---|---|---|
| **Lena (feelings and memory)** filter | `open-webui/lena_filter.py` | Before each message, reads `/api/lena/context` from Home Assistant (the time at home, who's home, how she feels and what she remembers) and adds it to the chat |
| **Lena (remember, forget, feel)** tool | `open-webui/lena_tools.py` | Lets her save to and remove from her memory list, and record how something made her feel |

Both talk only to your Home Assistant, using a token you give them. Neither needs anything installed.

## 1. Connect Ollama

Admin Panel → Settings → Connections → **Ollama API**: add your Ollama address (for example `http://ollama:11434` if they share a Docker network). The `lena` model you built appears in the model list.

## 2. Set up the lena model

Workspace → Models → `lena` (edit it; if it isn't listed, create a model with `lena` as its base).

- **Profile image:** upload `docs/assets/lena-avatar.png`, so her chats show her logo.
- **Capabilities:** tick **Vision**, so you can show her photos in the browser as well as in the app.
- **System Prompt:** leave it **empty**. Her character is built into the model and reaches her anyway; a system prompt here would only add to it, and any personality you write here could contradict hers.
- **Advanced Params → Function Calling:** **Native**. Gemma 4 supports tool calling natively, and this lets her use the Lena tool and Open WebUI's built-in tools.
- **Advanced Params → Keep Alive:** `-1`, so the model stays loaded between chats.
- **Advanced Params → Think (Ollama)** (called **Reasoning** in some versions): **Off** or Default. Gemma 4 only thinks when asked, and thinking adds nothing for house questions but time; with it on she can get stuck redrafting a one-line answer for minutes. Also leave the **Think** button in the chat box off. The Modelfile caps a reply at 4096 tokens as a backstop.
- Leave **Context Length** unset so the Modelfile's 32768 is used.

## 3. Get a Home Assistant token

In Home Assistant: your profile (bottom left) → **Security** → **Long-lived access tokens** → Create token. Name it "Open WebUI" and copy it.

Any user's token works; no admin rights are needed. A Home Assistant user just for Lena, without admin rights, is the safer choice. Open WebUI keeps the token on your server, and only Open WebUI admins can see function and tool settings.

## 4. Add the filter

1. Admin Panel → **Functions** → **+** (new function).
2. Paste the whole of `open-webui/lena_filter.py`, save, and switch it on.
3. Open its settings (the gear icon):

   | Setting | Value |
   |---|---|
   | ha_url | Your Home Assistant address **as the Open WebUI container can reach it**, for example `http://homeassistant:8123` or `https://ha.example.com` |
   | ha_token | The token from step 3 |
   | memory | On, to include her shared memory |
   | devices | On, to include the exact names of the devices you've exposed to Assist, by room |
   | cache_seconds | 60. How long to reuse Home Assistant's answer |

   The date and time come from Home Assistant, in your house's time zone; the model has no clock of its own.

   **Why devices:** Home Assistant's house tools find a device only by its exact name or one of its aliases: asked for "curtains", they don't find "Living Room Curtains". With the list of names (by room and kind, never states, so it can't be out of date), she asks for the right thing the first time. It's what Home Assistant's own voice assistant gives the model too. Only what's exposed under Settings → Voice assistants → Expose is listed. For words you use a lot, add an alias to the device there as well (say "Curtains" on Living Room Curtains): it helps her and Home Assistant's own Assist.

4. Workspace → Models → `lena` → **Filters**: tick **Lena (feelings and memory)**. Attach it to `lena` only, not globally, so your other models aren't affected.

## 5. Add the tool

1. Workspace → **Tools** → **+** (new tool).
2. Paste the whole of `open-webui/lena_tools.py` and save.
3. Open its settings: the same `ha_url` and `ha_token`, and `memory_list` = `todo.lena_memory` (the default).
4. Workspace → Models → `lena` → **Tools**: tick **Lena (remember, forget, feel)**.

The Lena app uses the model's tools and filters automatically, so there's nothing to set in the app.

## 6. Open WebUI's own memory

Open WebUI has a separate memory feature (Settings → Personalization → Memory) that applies to every model you use. With the filter and tool, Lena's memory is her shared list in Home Assistant instead, so we suggest turning Open WebUI's memory **off**, or she'll have two memories that don't agree. The app follows the same setting. The app's **Memory & knowledge** screen shows both: connect Home Assistant in the app's settings and a **Hers** tab shows her shared list next to Open WebUI's memories and your knowledge.

## 7. Test it

1. In Home Assistant, set **Lena Mood** to **Grumpy** by hand.
2. In a new chat with `lena`, ask "How's your day going?" She should sound grumpy without saying the word.
3. "Remember that Sam takes his coffee black." Check **Lena Memory** in Home Assistant's To-do lists.
4. "Thanks, Lena, you're the best." **Lena Moment** may change to **Pleased** or **Touched**. She uses it only when something genuinely lands, so don't expect it every time.

A change in Home Assistant can take up to a minute (`cache_seconds`) to reach her.

## If something's wrong

- **No feelings or memory in chats:** check the filter is switched on and ticked on the `lena` model, and that `ha_url` works from inside the Open WebUI container (`curl` it from there). Open WebUI's log shows `Lena filter: Home Assistant request failed` with the reason.
- **Nothing from Home Assistant at all:** open `<ha_url>/api/lena/context` with the token (for example with `curl -H "Authorization: Bearer <token>"`). A 404 means the Lena integration isn't set up; a 401 means the token is wrong.
- **She never uses the tool:** check Function Calling is **Native** and the tool is ticked on the model.
- **"Thought for 3 minutes" and she goes in circles** (the same sentence drafted over and over): thinking is on. Turn off **Think (Ollama)** on the `lena` model and the **Think** button in the chat box, and rebuild her from the current Modelfile, which tells her not to draft replies while thinking and stops a runaway reply at 4096 tokens.
- More in [Troubleshooting](../home-assistant/Troubleshooting.md).
