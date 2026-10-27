"""Lena's integration in a real Home Assistant: setup, entities, her AI tools, the view, events."""

import dataclasses
from datetime import timedelta

import pytest
from pytest_homeassistant_custom_component.common import MockConfigEntry, async_fire_time_changed

from homeassistant.core import HomeAssistant
from homeassistant.helpers import llm
from homeassistant.util import dt as dt_util

from custom_components.lena.const import DOMAIN

OPTIONS = {
    "people": ["person.alex", "person.sam"],
    "weather": "weather.home",
    "worries": ["binary_sensor.leak"],
    "morning": "06:30:00",
    "bedtime": "22:30:00",
    "away_hours": 4,
    "empty_hours": 3,
    "weather_chance": 100,
    "mood_hold_hours": 3,
    "mood_change_chance": 100,
    "moment_minutes": 20,
}


async def setup(hass: HomeAssistant, options=None) -> MockConfigEntry:
    hass.states.async_set("person.alex", "home", {"friendly_name": "Alex"})
    hass.states.async_set("person.sam", "home", {"friendly_name": "Sam"})
    hass.states.async_set("weather.home", "cloudy")
    hass.states.async_set("binary_sensor.leak", "off", {"friendly_name": "Leak sensor", "device_class": "moisture"})
    entry = MockConfigEntry(domain=DOMAIN, title="Lena", data={}, options=OPTIONS if options is None else options)
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    return entry


def context() -> llm.LLMContext:
    """An LLMContext for whichever fields this Home Assistant has."""
    values = {"platform": "test", "context": None, "language": "en", "assistant": "conversation", "device_id": None}
    names = {f.name for f in dataclasses.fields(llm.LLMContext)}
    return llm.LLMContext(**{k: v for k, v in values.items() if k in names})


def data(result):
    return getattr(result, "data", result)


async def test_entities(hass: HomeAssistant) -> None:
    await setup(hass)
    mood = hass.states.get("select.lena_mood")
    assert mood is not None and mood.state in mood.attributes["options"]
    assert "Grumpy" in mood.attributes["options"]
    assert hass.states.get("select.lena_energy").state in ("Steady", "Groggy", "Wired", "Drained")
    assert hass.states.get("select.lena_moment").state == "Nothing"
    assert hass.states.get("todo.lena_memory").state == "0"


async def test_set_by_hand_clears_reason(hass: HomeAssistant) -> None:
    await setup(hass)
    core = hass.data[DOMAIN]
    core.feelings.mood_reason = "It's raining."
    await hass.services.async_call("select", "select_option", {"entity_id": "select.lena_mood", "option": "Grumpy"}, blocking=True)
    state = hass.states.get("select.lena_mood")
    assert state.state == "Grumpy"
    assert state.attributes.get("reason") is None


async def test_llm_api_prompt_and_tools(hass: HomeAssistant) -> None:
    await setup(hass)
    await hass.services.async_call("select", "select_option", {"entity_id": "select.lena_mood", "option": "Grumpy"}, blocking=True)
    api = await llm.async_get_api(hass, "lena", context())
    assert "## How you feel right now" in api.api_prompt
    assert "Mood: Grumpy." in api.api_prompt
    assert "Mood: Cheerful." not in api.api_prompt  # only her current feelings
    assert "At home: " in api.api_prompt
    names = {t.name for t in api.tools}
    assert names == {"remember_fact", "forget_fact", "record_feeling"}

    result = await api.async_call_tool(llm.ToolInput(tool_name="remember_fact", tool_args={"fact": "Alex likes the office at seventy degrees."}))
    assert data(result)["saved"].startswith("Alex")
    await hass.async_block_till_done()
    assert hass.states.get("todo.lena_memory").state == "1"
    api = await llm.async_get_api(hass, "lena", context())
    assert "Alex likes the office at seventy degrees." in api.api_prompt

    result = await api.async_call_tool(llm.ToolInput(tool_name="record_feeling", tool_args={"moment": "Pleased", "reason": "Alex said thanks."}))
    assert data(result)["recorded"] is True
    await hass.async_block_till_done()
    moment = hass.states.get("select.lena_moment")
    assert moment.state == "Pleased" and moment.attributes["reason"] == "Alex said thanks."

    result = await api.async_call_tool(llm.ToolInput(tool_name="forget_fact", tool_args={"fact": "office temperature"}))
    await hass.async_block_till_done()
    assert hass.states.get("todo.lena_memory").state == "0"


async def test_merged_with_assist(hass: HomeAssistant) -> None:
    """Ticked together with Assist, as in an Ollama agent."""
    from homeassistant.setup import async_setup_component

    await setup(hass)
    assert await async_setup_component(hass, "homeassistant", {})
    await async_setup_component(hass, "llm", {})  # registers Assist on newer versions
    await hass.async_block_till_done()
    available = {a.id for a in llm.async_get_apis(hass)}
    if "assist" not in available:
        pytest.skip("Assist API not available in this test setup")
    api = await llm.async_get_api(hass, ["assist", "lena"], context())
    assert "How you feel right now" in api.api_prompt
    assert any(t.name.endswith("remember_fact") for t in api.tools)


async def test_feel_service_and_fade(hass: HomeAssistant) -> None:
    await setup(hass)
    await hass.services.async_call(DOMAIN, "feel", {"moment": "Amused", "reason": "A good pun."}, blocking=True)
    assert hass.states.get("select.lena_moment").state == "Amused"
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=21))
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_moment").state == "Nothing"


async def test_memory_todo_list(hass: HomeAssistant) -> None:
    await setup(hass)
    await hass.services.async_call("todo", "add_item", {"entity_id": "todo.lena_memory", "item": "Sam is allergic to cats."}, blocking=True)
    found = await hass.services.async_call(
        "todo", "get_items", {"entity_id": "todo.lena_memory"}, blocking=True, return_response=True
    )
    items = found["todo.lena_memory"]["items"]
    assert [i["summary"] for i in items] == ["Sam is allergic to cats."]
    await hass.services.async_call("todo", "update_item", {"entity_id": "todo.lena_memory", "item": items[0]["uid"], "status": "completed"}, blocking=True)
    assert hass.data[DOMAIN].memories() == []  # ticked off: retired
    await hass.services.async_call("todo", "remove_item", {"entity_id": "todo.lena_memory", "item": items[0]["uid"]}, blocking=True)
    assert hass.states.get("todo.lena_memory").state == "0"


async def test_worry_and_relief(hass: HomeAssistant) -> None:
    await setup(hass)
    hass.states.async_set("binary_sensor.leak", "on", {"friendly_name": "Leak sensor", "device_class": "moisture"})
    await hass.async_block_till_done()
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=3))
    await hass.async_block_till_done()
    moment = hass.states.get("select.lena_moment")
    assert moment.state == "Worried"
    assert moment.attributes["reason"] == "Leak sensor is detecting water."
    # Still worried after the usual fade time, because the leak hasn't cleared.
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=30))
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_moment").state == "Worried"
    hass.states.async_set("binary_sensor.leak", "off", {"friendly_name": "Leak sensor", "device_class": "moisture"})
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_moment").state == "Relieved"


async def test_arrival_after_a_long_time(hass: HomeAssistant, freezer) -> None:
    await setup(hass)
    hass.states.async_set("person.alex", "not_home", {"friendly_name": "Alex"})
    await hass.async_block_till_done()
    freezer.tick(timedelta(hours=30))
    hass.states.async_set("person.alex", "home", {"friendly_name": "Alex"})
    await hass.async_block_till_done()
    moment = hass.states.get("select.lena_moment")
    assert moment.state == "Delighted"
    assert moment.attributes["reason"] == "Alex just got home after 30 hours away."
    mood = hass.states.get("select.lena_mood")
    assert mood.state == "Fond"


async def test_hourly_drift(hass: HomeAssistant) -> None:
    await setup(hass)
    core = hass.data[DOMAIN]
    core.feelings.mood_since = dt_util.now() - timedelta(hours=5)
    before = core.feelings.mood
    core._on_hour(dt_util.now())  # what the hourly timer calls
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_mood").state != before


async def test_context_view(hass: HomeAssistant, hass_client) -> None:
    await setup(hass)
    client = await hass_client()
    response = await client.get("/api/lena/context")
    assert response.status == 200
    body = await response.json()
    assert "## How you feel right now" in body["context"]
    assert set(body["feelings"]) == {"mood", "mood_reason", "energy", "moment", "moment_reason"}
    assert body["memory"] == []


async def test_options_flow(hass: HomeAssistant) -> None:
    entry = await setup(hass, options={})
    result = await hass.config_entries.options.async_init(entry.entry_id)
    assert result["type"] == "form"
    user_input = dict(OPTIONS, weather_chance=30)
    result = await hass.config_entries.options.async_configure(result["flow_id"], user_input=user_input)
    assert result["type"] == "create_entry"
    await hass.async_block_till_done()
    assert entry.options["weather_chance"] == 30
    assert hass.states.get("select.lena_mood") is not None  # reloaded fine


async def test_config_flow_single(hass: HomeAssistant) -> None:
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": "user"})
    assert result["type"] == "form"
    result = await hass.config_entries.flow.async_configure(result["flow_id"], user_input={})
    assert result["type"] == "create_entry" and result["title"] == "Lena"
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": "user"})
    assert result["type"] == "abort"
