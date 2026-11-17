"""The "Lena" choice under Control Home Assistant in a conversation agent.

Tick it next to Assist in your Ollama agent's settings. Every request then carries the time,
who's home, how she feels and what she remembers, and she gets her tools: remember_fact,
forget_fact, update_fact, record_feeling, house_history, set_alarm, set_reminder,
cancel_alarm_or_reminder, list_alarms_and_reminders, set_volume, announce and, when there's a calendar she
may write to, add_calendar_event. Timers are Home Assistant's own tools, there for every Lena display (an
Assist satellite with timers).
Her character itself comes from the model's Modelfile.
"""

from __future__ import annotations

from homeassistant.core import HomeAssistant
from homeassistant.helpers import config_validation as cv, llm
import probatio as vol

from . import abilities, actions, brain, events, history
from .const import DOMAIN, HOUSE_MOMENTS

# How each tool behaves, for whoever lists her tools (Home Assistant's MCP server passes these on to MCP
# clients). Home Assistant assumes the worst of a tool that says nothing: that it writes, destroys and reaches
# outside the house.
READS = llm.ToolAnnotations(read_only=True, destructive=False, idempotent=True, open_world=False)
ADDS = llm.ToolAnnotations(destructive=False, open_world=False)
SETS = llm.ToolAnnotations(destructive=False, idempotent=True, open_world=False)
CHANGES = llm.ToolAnnotations(destructive=True, open_world=False)
REMOVES = llm.ToolAnnotations(destructive=True, idempotent=True, open_world=False)

SPOKEN = "You're talking through Home Assistant, so your replies are usually spoken aloud through the house's speakers."


def _core(hass: HomeAssistant):
    """The running core, found through hass.data rather than passed in, because Home Assistant creates
    the API and its tools itself.
    """
    core = hass.data.get(DOMAIN)
    if core is None:
        raise RuntimeError("Lena isn't set up")
    return core


class RememberTool(llm.Tool):
    """remember_fact: she saves a fact to the shared memory (the Lena Memory to-do list), so every app
    knows it.
    """
    name = "remember_fact"
    title = "Remember a fact"
    annotations = ADDS
    integration = DOMAIN
    description = (
        "Save something to your memory, which is shared with the chat app and the phone. Use it when someone "
        "asks you to remember something, or tells you a lasting fact that clearly matters."
    )
    parameters = vol.Schema({
        vol.Required("fact", description="One short third-person statement saying who it's about, under fifteen words"): cv.string,
    })

    async def async_call(self, hass, tool_input, llm_context):
        """Saves the fact and tells her what was saved."""
        item = _core(hass).remember(tool_input.tool_args["fact"])
        return llm.ToolResult(data={"saved": item["summary"]})


class ForgetTool(llm.Tool):
    """forget_fact: removes the saved fact that best matches what she was asked to forget."""
    name = "forget_fact"
    title = "Forget a fact"
    annotations = CHANGES
    integration = DOMAIN
    description = "Remove something from your memory when someone asks you to forget it."
    parameters = vol.Schema({
        vol.Required("fact", description="What to forget, in a few words; the closest saved memory is removed"): cv.string,
    })

    async def async_call(self, hass, tool_input, llm_context):
        """Forgets the closest match, or says nothing like it was saved, so she doesn't claim to have
        forgotten something she never knew.
        """
        removed = _core(hass).forget(tool_input.tool_args["fact"])
        return llm.ToolResult(data={"forgot": removed} if removed else {"forgot": None, "note": "Nothing like that was saved."})


class FeelTool(llm.Tool):
    """record_feeling: she sets a short-lived moment when something said to her genuinely lands
    (thanks, an insult, good news).
    """
    name = "record_feeling"
    title = "Record a feeling"
    annotations = SETS
    integration = DOMAIN
    description = (
        "Record how something the person just said or did made you feel. Use it only when it genuinely affects "
        "you: real thanks or praise, a kind or personal word, something funny, rudeness, or the same request yet "
        "again. Never for ordinary requests, at most once per reply, and don't mention using it."
    )

    def __init__(self, moments: list[str]) -> None:
        """Built per request with the moments she may set herself; the ones the house sets (Worried,
        Relieved, Delighted) are left out, so she can't fake or clear a worry.
        """
        self.parameters = vol.Schema({
            vol.Required("moment", description="How it made you feel"): vol.In(moments),
            vol.Required("reason", description="What happened, in a few words, written to yourself"): cv.string,
        })

    async def async_call(self, hass, tool_input, llm_context):
        """Sets the moment and reason; false if the moment isn't one of hers."""
        ok = _core(hass).feel(tool_input.tool_args["moment"], tool_input.tool_args.get("reason", ""))
        return llm.ToolResult(data={"recorded": ok}, error=not ok)


class AddEventTool(llm.Tool):
    """add_calendar_event: puts an event on one of the house's calendars through calendar.create_event
    (Home Assistant's own tools can only read calendars). The work is in events.py, shared with the
    lena.add_event action that Open WebUI uses.
    """
    name = "add_calendar_event"
    title = "Add a calendar event"
    annotations = llm.ToolAnnotations(destructive=False)
    integration = DOMAIN
    description = (
        "Add an event to a calendar when someone asks you to put something on it, book it or remind them on a "
        "day. Times are the house's local time. For an all-day event give only dates; for anything at a time "
        "give the date and time. Say what you added only after this succeeds."
    )

    def __init__(self, calendars: list[str]) -> None:
        """Built per request with the calendars she may add to, so she can only name real ones."""
        self.parameters = vol.Schema({
            vol.Required("calendar", description="The calendar's exact name"): vol.In(calendars),
            vol.Required("title", description="What it is, in a few words, e.g. Dentist"): cv.string,
            vol.Required("start", description="2026-11-14 for all day, or 2026-11-14T15:30 at a time"): cv.string,
            vol.Optional(
                "end",
                description="Optional. For all day, the last day (2026-11-16); at a time, when it ends (2026-11-14T16:30). Without it: one day, or one hour",
            ): cv.string,
            vol.Optional("location", description="Optional. Where it is"): cv.string,
            vol.Optional("description", description="Optional. Any details worth keeping"): cv.string,
        })

    async def async_call(self, hass, tool_input, llm_context):
        """Adds the event, or tells her what to fix (the error says how) so she can try once more."""
        args = tool_input.tool_args
        try:
            added = await events.add_event(
                hass, llm_context.context, args.get("calendar"), args.get("title", ""), args.get("start", ""),
                args.get("end"), args.get("location"), args.get("description"),
            )
        except events.EventError as error:
            return llm.ToolResult(data={"added": None, "error": str(error)}, error=True)
        return llm.ToolResult(data=added)


ROOM = "Optional. The room, when they name one (\"tell the kitchen\", \"remind the bedroom\"); leave out to use the display you're talking through"


class _DisplayTool(llm.Tool):
    """A tool that acts on a display: the one in the room named, else the one she's talking through
    (Assist knows which device a question came from).
    """
    integration = DOMAIN

    def run(self, core, args: dict, device_id: str | None):
        raise NotImplementedError

    async def async_call(self, hass, tool_input, llm_context):
        """Runs it, or tells her what's missing (which room, a time she can't read) so she can ask or fix it.
        A miss is marked as an error, which Home Assistant hands the model along with the words.
        """
        try:
            result = self.run(_core(hass), dict(tool_input.tool_args), llm_context.device_id)
            if hasattr(result, "__await__"):
                result = await result
            return llm.ToolResult(data=result)
        except (abilities.AbilityError, history.HistoryError) as error:
            return llm.ToolResult(data={"done": False, "error": str(error)}, error=True)


class SetAlarmTool(_DisplayTool):
    """set_alarm: an alarm at a time of day."""
    name = "set_alarm"
    title = "Set an alarm"
    annotations = ADDS
    description = "Set an alarm that rings on the display at the next time the clock shows that time."
    parameters = vol.Schema({
        vol.Required("time", description="24-hour HH:MM, e.g. 06:45 or 19:00"): cv.string,
        vol.Optional("label", description="Optional. What it's for"): cv.string,
        vol.Optional("room", description=ROOM): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.set_alarm(core, args["time"], args.get("label"), device_id, args.get("room"))


class SetReminderTool(_DisplayTool):
    """set_reminder: she says something on the display at a time, or after a while."""
    name = "set_reminder"
    title = "Set a reminder"
    annotations = ADDS
    description = ("Remind them of something: at a time (\"at 7\") or after a while (\"in 20 seconds\"). She says the "
                   "message out loud on the display when it's time. Give either at or in_seconds. For something on a "
                   "later day than next week, add a calendar event instead.")
    parameters = vol.Schema({
        vol.Required("message", description="What to remind them of, said to them, e.g. Time to flip the chicken"): cv.string,
        vol.Optional("at", description="When: 24-hour HH:MM for the next time the clock shows it (19:00), or a date and time (2026-11-08T07:30)"): cv.string,
        vol.Optional("in_seconds", description="Or: how many seconds from now"): vol.Coerce(int),
        vol.Optional("room", description=ROOM): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.set_reminder(core, args["message"], args.get("at"), args.get("in_seconds"), device_id, args.get("room"))


class CancelReminderTool(_DisplayTool):
    """cancel_alarm_or_reminder: cancels her alarms or reminders (timers are Home Assistant's own tools)."""
    name = "cancel_alarm_or_reminder"
    title = "Cancel an alarm or reminder"
    annotations = REMOVES
    description = "Cancel an alarm or a reminder: the one named, or all of a kind. (For timers, use the timer tools.)"
    parameters = vol.Schema({
        vol.Required("kind", description="alarm or reminder"): vol.In(["alarm", "reminder"]),
        vol.Optional("label", description="Optional. What it's for"): cv.string,
        vol.Optional("all", description="True to cancel all of that kind"): cv.boolean,
        vol.Optional("room", description=ROOM): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.cancel(core, args["kind"], args.get("label"), bool(args.get("all")), device_id, args.get("room"))


class ListRemindersTool(_DisplayTool):
    """list_alarms_and_reminders: what's set (timers are Home Assistant's own tools)."""
    name = "list_alarms_and_reminders"
    title = "List alarms and reminders"
    annotations = READS
    description = "What alarms and reminders are set, on this display or everywhere. (For timers, use the timer tools.)"
    parameters = vol.Schema({
        vol.Optional("everywhere", description="True for every display in the house"): cv.boolean,
        vol.Optional("room", description=ROOM): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.list_countdowns(core, device_id, args.get("room"), bool(args.get("everywhere")), timers=False)


class SetVolumeTool(_DisplayTool):
    """set_volume: how loudly she speaks."""
    name = "set_volume"
    title = "Set her volume"
    annotations = ADDS
    description = "Change how loudly you speak on the display: a level, or quieter or louder."
    parameters = vol.Schema({
        vol.Optional("level", description="0 to 100"): vol.Coerce(int),
        vol.Optional("change", description="quieter or louder"): vol.In(["quieter", "louder"]),
        vol.Optional("room", description=ROOM): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.set_volume(core, args.get("level"), args.get("change"), device_id, args.get("room"))


class AnnounceTool(_DisplayTool):
    """announce: the intercom."""
    name = "announce"
    title = "Announce in a room"
    annotations = ADDS
    description = ("Say something out loud on the display in another room, like an intercom (\"tell the kitchen dinner's "
                   "ready\"). With listen, she listens there for an answer and answers it herself.")
    parameters = vol.Schema({
        vol.Required("message", description="What to say there, as you'd say it to them"): cv.string,
        vol.Required("room", description="The room to say it in"): cv.string,
        vol.Optional("listen", description="True to listen for an answer after saying it"): cv.boolean,
    })

    def run(self, core, args, device_id):
        return abilities.announce(core, args["message"], bool(args.get("listen")), None, args["room"])


class UpdateFactTool(_DisplayTool):
    """update_fact: corrects a memory in place."""
    name = "update_fact"
    title = "Correct a fact"
    annotations = CHANGES
    description = ("Correct something you remember when it's changed or was wrong (\"Alex moved to the night shift\"), "
                   "instead of saving a second, conflicting memory.")
    parameters = vol.Schema({
        vol.Required("old", description="The memory to change, in a few words"): cv.string,
        vol.Required("new", description="What it should say now: one short third-person statement"): cv.string,
    })

    def run(self, core, args, device_id):
        return abilities.update_fact(core, args["old"], args["new"])


class HistoryTool(_DisplayTool):
    """house_history: what happened in the house."""
    name = "house_history"
    title = "Look up the house's history"
    annotations = READS
    description = ("Look back at the house's history: when something last happened (\"when did the garage door last "
                   "open?\"), how long something lasted (\"how long was the back door open?\"), or what happened today "
                   "(\"what did I miss?\": people coming and going, doors, locks, alarms).")
    parameters = vol.Schema({
        vol.Required("question", description="last_change, how_long or what_happened"): vol.In(["last_change", "how_long", "what_happened"]),
        vol.Optional("name", description="What to look up, by its name in the home (not for what_happened)"): cv.string,
        vol.Optional("state", description="Optional. The state asked about: open, on, home, locked"): cv.string,
        vol.Optional("hours", description="Optional. How far back, in hours; today by default"): vol.Coerce(float),
    })

    def run(self, core, args, device_id):
        return actions.ask_history(core.hass, args)


class LenaAPI(llm.API):
    """Her context and tools, added to whatever else the agent uses (usually Assist)."""

    async def async_get_api_instance(self, llm_context: llm.LLMContext) -> llm.APIInstance:
        """Her context (time, home, feelings, memory) as the API prompt, ending with the reminder to
        call a tool before saying something's done, and her tools, for one conversation.
        """
        core = _core(self.hass)
        moments = [m for m in core.catalog.moments if m not in HOUSE_MOMENTS]
        tools: list[llm.Tool] = [
            RememberTool(), ForgetTool(), UpdateFactTool(), HistoryTool(),
            SetAlarmTool(), SetReminderTool(), CancelReminderTool(), ListRemindersTool(),
            SetVolumeTool(), AnnounceTool(),
        ]
        if moments:
            tools.append(FeelTool(moments))
        calendars = sorted(events.writable_calendars(self.hass))
        if calendars:
            tools.append(AddEventTool(calendars))
        # The reminder goes last: it only works there (see HOUSE_REMINDER in brain.py).
        prompt = "\n\n".join([SPOKEN, core.context(), brain.MEMORY_RULES, brain.HOUSE_REMINDER])
        return llm.APIInstance(api=self, api_prompt=prompt, llm_context=llm_context, tools=tools)
