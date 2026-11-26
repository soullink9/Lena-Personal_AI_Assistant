# Lena in Open WebUI

[← Back to Getting started](../Getting-Started.md)

Open WebUI is where you chat with Lena in a browser, and it's what the Lena app talks to. Her character comes from the `lena` model itself. Two small add-ons bring over what the Lena integration in Home Assistant knows: how she feels right now, and her shared memory.

| Piece | File | What it does |
|---|---|---|
| **Lena (feelings and memory)** filter | `open-webui/lena_filter.py` | Before each message, reads `/api/lena/context` from Home Assistant (the time at home, who's home, how she feels and what she remembers) and adds it to the chat |
| **Lena (remember, forget, feel, calendar)** tool | `open-webui/lena_tools.py` | Lets her save to and remove from her memory list, record how something made her feel, and add events to your Home Assistant calendars |

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

A Home Assistant user just for Lena, without admin rights, is the safer choice: allow it under **Who may use Lena** in Lena's options (Settings → Devices & services → Lena → Configure), or the filter gets "may not use Lena". An admin's token works too. Open WebUI keeps the token on your server, and only Open WebUI admins can see function and tool settings.

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
   | time_passing | On, to tell her how much time passed between messages (below) |
   | who_and_where | On, to tell her who she's talking with (the Open WebUI user's name, matched to a Home Assistant person) and, from the Lena app, which room |
   | cache_seconds | 60. How long to reuse Home Assistant's answer |

   The date and time come from Home Assistant, in your house's time zone; the model has no clock of its own.

   **Why time_passing:** a model reads every message as if it came a moment ago, so coming back to a chat the next day, she'd carry on with yesterday's topic as if you'd never left. With it on, wherever 30 minutes or more passed between two messages she's told so, and before your new message how long it's been and that the earlier conversation is over; she answers what you say now. The times come from the chat as Open WebUI saved it, in your browser's time zone, so this works even without Home Assistant. The notes go with each request only and never show in the chat. The Lena app adds the same notes itself.

   **Why who_and_where:** she knows whose memories come first, when you last talked, and keeps it soft in a bedroom at night. Give each Open WebUI user the same name as their person in Home Assistant (Settings → People). A shared wall display doesn't send a user, so there she talks to the household.

   **Why devices:** Home Assistant's house tools find a device only by its exact name or one of its aliases: asked for "curtains", they don't find "Living Room Curtains". With the list of names (by room and kind, never states, so it can't be out of date), she asks for the right thing the first time. It's what Home Assistant's own voice assistant gives the model too. Only what's exposed under Settings → Voice assistants → Expose is listed. For words you use a lot, add an alias to the device there as well (say "Curtains" on Living Room Curtains): it helps her and Home Assistant's own Assist.

4. Workspace → Models → `lena` → **Filters**: tick **Lena (feelings and memory)**. Attach it to `lena` only, not globally, so your other models aren't affected.

## 5. Add the tool

1. Workspace → **Tools** → **+** (new tool).
2. Paste the whole of `open-webui/lena_tools.py` and save.
3. Open its settings: the same `ha_url` and `ha_token`, and `memory_list` = `todo.lena_memory` (the default).
4. Workspace → Models → `lena` → **Tools**: tick **Lena (memory, feelings, calendar, timers, intercom, history)**.

Its `add_calendar_event` runs the `lena.add_event` action in Home Assistant, so it can add to the same calendars as her voice: those exposed to Assist that take new events (see [Calendars](../home-assistant/Integration.md#calendars)). Its timers, alarms, reminders, volume, intercom, memory correction and house history run the same `lena.*` actions as her tools in Assist (see [Her tools on the displays](../home-assistant/Integration.md#her-tools-on-the-displays)); the Lena app tells it which display is asking, so they ring where you asked. Updating from an earlier version: paste the new file over the old tool; its settings are kept.

The Lena app uses the model's tools and filters automatically, so there's nothing to set in the app.

### Home Assistant's house tools

Lights, covers and everything else in the house reach her through Home Assistant's own MCP server (Home Assistant's **Model Context Protocol Server** integration), added in Open WebUI under Admin Panel → Settings → **External Tools** and ticked under Workspace → Models → `lena` → **Tools**. Open WebUI names each of its tools after the **ID** you give the server there: with the ID `home_assistant`, Home Assistant's `homeassistant__GetLiveContext` reaches her as `home_assistant_homeassistant__GetLiveContext`.

- **ID:** use only letters, digits and underscores, for example `home_assistant`. A model tends to write a hyphen in a tool name as an underscore, and Open WebUI answers "Tool not found" to a name that's off by one character; with `home-assistant` she asked "any lights on?" and couldn't look. The Lena filter puts the right name back when she gets only case or punctuation wrong, so an existing hyphenated ID works too, but a plain one avoids the slip.

### Turn Tool Search off

Open WebUI's **Tool Search** (Admin Panel → Settings → Interface, off unless you turned it on) hands the model only short tools. Any tool whose definition is longer than **Tool Search Defer Threshold** (400 characters unless changed) is hidden behind `search_tools` until she searches for it, and she rarely does. Most of her tools are longer than that, because their descriptions tell her when and how to use them: with Tool Search on she can't save a memory (`remember`) or record a feeling (`feel`), and says she did anyway. Leave Tool Search **off**.

If you need it on for other models, put her tools in **Always Loaded** instead, so she always has them:

```
remember,forget,update_fact,feel,home_assistant_*
```

`home_assistant` is the ID you gave Home Assistant's MCP server in Open WebUI (see [Home Assistant's house tools](#home-assistants-house-tools)); change it if yours differs.

### Context Compaction

Open WebUI's **Context Compaction** (Admin Panel → Settings → Interface, off unless you turned it on) has the model summarize the older part of a long chat once it passes a token threshold, and the answer waits for that summary. On a local model that wait is far longer than the answer.

**The threshold must be well above Lena's prompt without the conversation.** Open WebUI also checks between the rounds of an answer that uses tools, and there it counts everything the model was sent: her character (the Modelfile), her context from Home Assistant (feelings, memory, the Goodnight check, the device names), every tool's definition, and the tool results. That alone is often 10,000 tokens or more. If the threshold is close to it, every round is over, and "Context compacted" shows up after every tool call, with a summary written each time. A smaller conversation can't help then; only a higher threshold can.

To size it, ask her something short that uses a tool ("Any lights on?") in a new chat, then open the reply's info (the **i** under it): **prompt tokens** (prompt_eval_count) is roughly her prompt without the conversation. Set the threshold to that plus about 12,000, but never above `num_ctx` (32768) less the longest reply (`num_predict`, 4096), so **28000** at most: past that her prompt and reply no longer fit, and Ollama cuts the prompt instead of Open WebUI summarizing it. **24000** suits most homes. Set it on the `lena` model (Workspace → Models → lena → Advanced Params → Context Compaction Threshold) or for every model in the Admin Panel; the lower one counts. Setting **Context Compaction Model** to a small, fast model makes each summary quicker. Or turn Context Compaction off: the app already trims her conversation, and Open WebUI's own page then sends whole chats, which Ollama shortens to fit `num_ctx`.

**If her prompt alone is near 20,000 tokens, make it smaller rather than the threshold higher.** With Native function calling, Open WebUI offers her its own built-in tools as well as hers, and sends every one's definition with every request. With its default features on, that's about 25 tools and 6,000 to 7,000 tokens, most of them things she has her own tools for or doesn't use. Turn off the ones she doesn't need under Workspace → Models → `lena` → **Builtin Tools** (approximate sizes, from Open WebUI 0.13):

| Builtin Tools category | Tools | About | Lena's own |
|---|---|---|---|
| Memory | 8 | 1,200 tokens | `remember`, `forget`, `update_fact` and her shared memory |
| Calendar | 4 | 1,000 tokens | `add_calendar_event` |
| Automations | 5 | 900 tokens | `set_reminder`, `set_alarm`, `set_timer` |
| Notes | 4 | 800 tokens | |
| Chats | 3 | 450 tokens | |
| Skills, Tools (admins only) | 2 | 400 tokens | |
| Time | 2 | 300 tokens | her context and the app give her the time |
| Knowledge | 2 | 300 tokens | |
| Tasks | 2 | 200 tokens | |

Keep **Web Search** if she should search the web. Leave her own tools' descriptions as they are: they're what makes her use them right. The tool results count too: a house question that calls `GetLiveContext` adds the state of everything exposed to Assist, so expose only what she needs (Settings → Voice assistants → Expose). In Home Assistant's **Model Context Protocol Server** options, choose **Assist** only: the Lena tool above already gives her her own tools, and choosing Lena there as well sends a second copy of each (`remember_fact` beside `remember`, and so on).

The Lena app keeps compaction off the path of her answers as far as it can: it sends each question with only as much conversation as fits under the threshold beside the rest of her prompt (measured from the size the model reported for her last answer), with room left for tool results, and asks Open WebUI to compact the chat a few minutes after you stop talking, when it's getting long. Her next answer then starts from that summary. When the threshold is too low for her prompt, the app's log (Settings → About → Logs) says so and what to raise it to, never above what her context (Settings → Model) holds beside her reply; when no threshold fits, it says her prompt has to get smaller.

### Turn off its telemetry and update check

Lena never sends telemetry ([Privacy and Security](../app/Privacy-and-Security.md#no-telemetry)), and Open WebUI shouldn't either. Add these to Open WebUI's environment (its Docker container, or the shell you start it from) and restart it:

| Variable | Why |
|---|---|
| `ENABLE_VERSION_UPDATE_CHECK=false` | Otherwise every admin's page asks GitHub whether a newer Open WebUI is out. |
| `HF_HUB_DISABLE_TELEMETRY=1` | Its downloads from Hugging Face (the built-in Whisper and embedding models) then send no usage details. |
| `SCARF_NO_ANALYTICS=true`, `DO_NOT_TRACK=true`, `ANONYMIZED_TELEMETRY=false` | Turn off the libraries' own analytics. The official Docker image sets these already; set them if you installed it with pip. |

Leave `ENABLE_OTEL` off (its default): it sends traces and metrics to a collector.

## 6. Open WebUI's own memory

Open WebUI has a separate memory feature (Settings → Personalization → Memory) that applies to every model you use. With the filter and tool, Lena's memory is her shared list in Home Assistant instead, so we suggest turning Open WebUI's memory **off**, or she'll have two memories that don't agree. The app follows the same setting. The app's **Memory and knowledge** screen shows both: connect Home Assistant in the app's settings and a **Hers** tab shows her shared list next to Open WebUI's memories and your knowledge.

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
- **She says she saved something, but Lena Memory doesn't have it:** she didn't have the tool to save it. Open her reasoning ("Thought for…"): if `remember` isn't in the tools she lists, Tool Search hid it. See [Turn Tool Search off](#turn-tool-search-off).
- **She answers nothing to a house question, and her reasoning shows `Error: Tool "…" not found.`:** the name she used isn't one Open WebUI gave her. Update the Lena filter (it corrects a name that's off only in case or punctuation, such as `home_assistant_…` for `home-assistant_…`), and give Home Assistant's MCP server an ID of letters, digits and underscores only (see [Home Assistant's house tools](#home-assistants-house-tools)). The filter logs each correction as `Lena filter: she called …`.
- **"Thought for 3 minutes" and she goes in circles** (the same sentence drafted over and over): thinking is on. Turn off **Think (Ollama)** on the `lena` model and the **Think** button in the chat box, and rebuild her from the current Modelfile, which tells her not to draft replies while thinking and stops a runaway reply at 4096 tokens.
- More in [Troubleshooting](../home-assistant/Troubleshooting.md).
