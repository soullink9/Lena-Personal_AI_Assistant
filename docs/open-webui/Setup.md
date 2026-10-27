# Lena in Open WebUI

[← Back to Getting started](../Getting-Started.md)

Open WebUI is where you chat with Lena in a browser, and it's what the Lena app talks to. Her character comes from the `lena` model itself. Two small add-ons bring over what Home Assistant knows: how she feels right now, and her shared memory.

| Piece | File | What it does |
|---|---|---|
| **Lena (feelings and memory)** filter | `open-webui/lena_filter.py` | Before each message, asks Home Assistant how she feels and what she remembers, and adds it to the chat |
| **Lena (remember, forget, feel)** tool | `open-webui/lena_tools.py` | Lets her save to and remove from her memory list, and record how something made her feel |

Both talk only to your Home Assistant, using a token you give them. Neither needs anything installed.

## 1. Connect Ollama

Admin Panel → Settings → Connections → **Ollama API**: add your Ollama address (for example `http://ollama:11434` if they share a Docker network). The `lena` model you built appears in the model list.

## 2. Set up the lena model

Workspace → Models → `lena` (edit it; if it isn't listed, create a model with `lena` as its base).

- **System Prompt:** leave it **empty**. Her character is built into the model and reaches her anyway; a system prompt here would only add to it, and any personality you write here could contradict hers.
- **Advanced Params → Function Calling:** **Native**. Gemma 4 supports tool calling natively, and this lets her use the Lena tool and Open WebUI's built-in tools.
- **Advanced Params → Keep Alive:** `-1`, so the model stays loaded between chats.
- Leave **Context Length** unset so the Modelfile's 32768 is used.

## 3. Get a Home Assistant token

In Home Assistant: your profile (bottom left) → **Security** → **Long-lived access tokens** → Create token. Name it "Open WebUI" and copy it.

Home Assistant only lets **administrators** render templates, which the filter needs to read her feelings, so create the token on an admin account. Open WebUI keeps it on your server, and only Open WebUI admins can see function and tool settings. If you'd rather not, turn off **feelings** in the filter's settings; memory works with any user's token.

## 4. Add the filter

1. Admin Panel → **Functions** → **+** (new function).
2. Paste the whole of `open-webui/lena_filter.py`, save, and switch it on.
3. Open its settings (the gear icon):

   | Setting | Value |
   |---|---|
   | ha_url | Your Home Assistant address **as the Open WebUI container can reach it**, for example `http://homeassistant:8123` or `https://ha.example.com` |
   | ha_token | The token from step 3 |
   | feelings | On (needs `lena.jinja` in Home Assistant; see [Home Assistant installation](../home-assistant/Installation.md#3-copy-in-her-files)) |
   | memory_list | Her memory to-do list, usually `todo.lena_s_memory`. Empty to skip memory |
   | cache_seconds | 60. How long to reuse Home Assistant's answer |

4. Workspace → Models → `lena` → **Filters**: tick **Lena (feelings and memory)**. Attach it to `lena` only, not globally, so your other models aren't affected.

## 5. Add the tool

1. Workspace → **Tools** → **+** (new tool).
2. Paste the whole of `open-webui/lena_tools.py` and save.
3. Open its settings: the same `ha_url` and `ha_token`, the same `memory_list`, and `feel_script` = `script.lena_feel`.
4. Workspace → Models → `lena` → **Tools**: tick **Lena (remember, forget, feel)**.

The Lena app uses the model's tools and filters automatically, so there's nothing to set in the app.

## 6. Open WebUI's own memory

Open WebUI has a separate memory feature (Settings → Personalization → Memory) that applies to every model you use. With the filter and tool, Lena's memory is her shared list in Home Assistant instead, so we suggest turning Open WebUI's memory **off**, or she'll have two memories that don't agree. The app follows the same setting. The app's **Memory & knowledge** screen shows Open WebUI's memories and your knowledge collections, not her shared list; see her list in Home Assistant.

## 7. Test it

1. In Home Assistant, set her mood helper to **Grumpy** by hand.
2. In a new chat with `lena`, ask "How's your day going?" She should sound grumpy without saying the word.
3. "Remember that Sam takes his coffee black." Check the to-do list in Home Assistant.
4. "Thanks, Lena, you're the best." Her Moment helper may change to **Pleased** or **Touched**. She uses it only when something genuinely lands, so don't expect it every time.

A change in Home Assistant can take up to a minute (`cache_seconds`) to reach her.

## If something's wrong

- **No feelings or memory in chats:** check the filter is switched on and ticked on the `lena` model, and that `ha_url` works from inside the Open WebUI container (`curl` it from there). Open WebUI's log shows `Lena filter: Home Assistant request failed` with the reason.
- **Feelings missing but memory works:** the token isn't an admin's, or `lena.jinja` isn't in Home Assistant's `custom_templates` folder (reload custom templates after copying it).
- **She never uses the tool:** check Function Calling is **Native** and the tool is ticked on the model.
- More in [Troubleshooting](../home-assistant/Troubleshooting.md).
