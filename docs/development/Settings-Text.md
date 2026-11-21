# Writing Settings text

How the words in Lena's Settings are written, so every page sounds like the same app. `tools/check.py` enforces the mechanical parts (see the end); `python3 tools/settings_text.py` lists every title and detail in Settings for a read-through.

## Voice

- Talk to the person, about Lena: "you" and "she". "Quieter before 8 AM", not "Applies a time-based gain".
- Say what happens, not how it works.
- Warm, never cute, and no blame: "Couldn't reach Home Assistant", not "You entered a wrong address".

## One name per thing

| Use | Not | Why |
|---|---|---|
| screensaver (Lena's) | screen saver | One spelling for hers. Android's own menu is quoted as Android writes it ("Screen saver"). |
| Open WebUI, Home Assistant, Gitea | OWUI, HA, Hass | Their own names |
| sign in (verb), sign-in (noun) | log in, login | What Open WebUI and Home Assistant say |
| address | URL, host | What people type |
| this TV, this tablet, this phone (`Tv.thisDevice`) | "this phone" on every device | Say the device it's on |
| and | & | One style, and it reads aloud the same |
| US spelling: color, optimize, customize | colour, optimise, customise | One spelling |
| 8 AM, 10 PM | 8am, 20:00 | Android's default in the US |

Two things keep their two names on purpose: **Her look** is how she looks while you talk to her, and **Lena's look** is her theme for Open WebUI's pages.

## Patterns

| Part | Rule | Example |
|---|---|---|
| Title | What it is or does, sentence case, no period, about 4 words | Adjust her volume |
| Detail | One line: the current value, or what it does; under about 90 characters | Quieter before 8 AM and after 10 PM |
| Needs something | Ends the detail: "Needs Home Assistant." | Her voice follows her mood |
| Off state | "Off: …" when off isn't obvious | Off: Open WebUI's own look |
| Example in a field | The placeholder, never the label | https://ha.example.com |
| In progress | Ends with … | Looking in Home Assistant… |
| Steps | Numbered titles only where order matters | 1. Let Lena install apps |

A title names the feature, never a state: when something needs Home Assistant first, the item keeps its name and its detail says "Needs Home Assistant".

## Checklist for each string

1. **Clear**: one meaning, and it says what happens.
2. **Short**: one line on a phone, readable from the sofa on a TV.
3. **Consistent**: the names above.
4. **True on this device**: a TV has no chat, a tablet isn't "this phone".
5. **Kind**: no blame, no jargon.
6. **Accessible**: meaning never by color alone; TalkBack reads the title and detail as one sentence.

## Auditing

1. `python3 tools/settings_text.py` prints every title and detail in Settings with its line, and marks the ones worth a look (long details, "phone" in the text, the same title twice).
2. Read each against the checklist; fix the text, and the docs that quote it, in the same commit.
3. `python3 tools/check.py` then keeps the mechanical rules: none of the "Not" spellings above in the app's text, no "&" in a title, and Lena's "screensaver" as one word.
