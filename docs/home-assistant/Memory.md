# Memory

[← Back to Home Assistant installation](Installation.md)

Lena has one memory, shared everywhere she lives: the **Lena Memory** to-do list (`todo.lena_memory`) that the integration creates. What you tell her in the kitchen she knows on your phone, and the other way round.

| Where | How she reads it | How she saves and forgets |
|---|---|---|
| Home Assistant voice | Always in her context, through the Lena choice in your agent | Her `remember_fact` and `forget_fact` tools |
| Open WebUI and the Lena app | The Lena filter adds it to every chat | The Lena tool's remember and forget |
| You | The To-do lists panel in Home Assistant, or **Memory and knowledge → Hers** in the app | Edit the list directly |

## How she saves

When someone asks her to remember something, or tells her a lasting fact that clearly matters, she adds one short third-person line, like "Alex likes the office at seventy degrees." She's told not to save passing moods, one-off requests, duplicates or secrets, and to confirm only once it's saved.

Some small models say they saved something without actually doing it. Check the list after your first few tries.

## Forgetting and tidying

- Ask her to forget something. She removes the saved memory that best matches what you said.
- Or edit the list yourself: delete an item, reword it, or **tick it off** to retire it. She only reads open items.
- Keep it short. It's added to every request.

## She tidies it herself

Every 7 days by default, at bedtime, Lena tidies her own memory with Home Assistant's **AI Task** (your own model, through **Settings → System → General → AI suggestions**, or the AI Task chosen under Configure → **Tidy it with**):

- **Combines** lines about the same thing ("Alex likes coffee." and "Alex takes it black." become "Alex likes coffee, black.").
- **Clarifies** a vague line into one clear sentence, using only what it says.
- **Retires** lines that are outdated (a date that has passed, a fact a newer line replaces) or were only for a moment, by **ticking them off**: they stay on the list, out of her prompt, as when you tick one off yourself.

She never touches lines you've ticked off, never retires more than half of what she remembers at once, and changes nothing if the model's answer doesn't add up. Afterwards a notification says exactly what changed; the action **Lena: Undo memory tidy** (`lena.undo_memory_tidy`) puts it all back. **Lena: Tidy memory** (`lena.tidy_memory`) runs it now, and so does **Optimize memory** in the Lena app (Settings → tap Software version → Development), which shows what changed with Undo. Set Configure → **She tidies her memory every** to 0 to turn it off.

## Privacy

Everything on the list is shared with everyone who talks to Lena, in every app, and is sent to your own Ollama server with their messages. Don't keep passwords, door codes or anything similar in it. She's told to refuse to save those, but don't rely on that.
