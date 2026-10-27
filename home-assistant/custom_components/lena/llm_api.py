"""The "Lena" choice under Control Home Assistant in a conversation agent.

Tick it next to Assist in your Ollama agent's settings. Every request then carries the time,
who's home, how she feels and what she remembers, and she gets three tools: remember_fact,
forget_fact and record_feeling. Her character itself comes from the model's Modelfile.
"""

from __future__ import annotations

from homeassistant.core import HomeAssistant
from homeassistant.helpers import config_validation as cv, llm

from . import brain
from .compat import tool_result, vol
from .const import DOMAIN, HOUSE_MOMENTS

SPOKEN = "You're talking through Home Assistant, so your replies are usually spoken aloud through the house's speakers."


def _core(hass: HomeAssistant):
    core = hass.data.get(DOMAIN)
    if core is None:
        raise RuntimeError("Lena isn't set up")
    return core


class RememberTool(llm.Tool):
    name = "remember_fact"
    integration = DOMAIN
    description = (
        "Save something to your memory, which is shared with the chat app and the phone. Use it when someone "
        "asks you to remember something, or tells you a lasting fact that clearly matters."
    )
    parameters = vol.Schema({
        vol.Required("fact", description="One short third-person statement saying who it's about, under fifteen words"): cv.string,
    })

    async def async_call(self, hass, tool_input, llm_context):
        item = _core(hass).remember(tool_input.tool_args["fact"])
        return tool_result({"saved": item["summary"]})


class ForgetTool(llm.Tool):
    name = "forget_fact"
    integration = DOMAIN
    description = "Remove something from your memory when someone asks you to forget it."
    parameters = vol.Schema({
        vol.Required("fact", description="What to forget, in a few words; the closest saved memory is removed"): cv.string,
    })

    async def async_call(self, hass, tool_input, llm_context):
        removed = _core(hass).forget(tool_input.tool_args["fact"])
        return tool_result({"forgot": removed} if removed else {"forgot": None, "note": "Nothing like that was saved."})


class FeelTool(llm.Tool):
    name = "record_feeling"
    integration = DOMAIN
    description = (
        "Record how something the person just said or did made you feel. Use it only when it genuinely affects "
        "you: real thanks or praise, a kind or personal word, something funny, rudeness, or the same request yet "
        "again. Never for ordinary requests, at most once per reply, and don't mention using it."
    )

    def __init__(self, moments: list[str]) -> None:
        self.parameters = vol.Schema({
            vol.Required("moment", description="How it made you feel"): vol.In(moments),
            vol.Required("reason", description="What happened, in a few words, written to yourself"): cv.string,
        })

    async def async_call(self, hass, tool_input, llm_context):
        ok = _core(hass).feel(tool_input.tool_args["moment"], tool_input.tool_args.get("reason", ""))
        return tool_result({"recorded": ok})


class LenaAPI(llm.API):
    """Her context and tools, added to whatever else the agent uses (usually Assist)."""

    async def async_get_api_instance(self, llm_context: llm.LLMContext) -> llm.APIInstance:
        core = _core(self.hass)
        moments = [m for m in core.catalog.moments if m not in HOUSE_MOMENTS]
        tools: list[llm.Tool] = [RememberTool(), ForgetTool()]
        if moments:
            tools.append(FeelTool(moments))
        prompt = "\n\n".join([SPOKEN, core.context(), brain.MEMORY_RULES])
        return llm.APIInstance(api=self, api_prompt=prompt, llm_context=llm_context, tools=tools)
