"""Lena's integration in a real Home Assistant: setup, entities, her AI tools, the view, events."""

import dataclasses
from datetime import timedelta

import pytest
from pytest_homeassistant_custom_component.common import MockConfigEntry, async_fire_time_changed, async_mock_service

from homeassistant.core import HomeAssistant, SupportsResponse
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
    """Sets Lena up the way a user would, with two people at home, cloudy weather and a leak sensor
    that's off, and returns the entry.
    """
    hass.states.async_set("person.alex", "home", {"friendly_name": "Alex"})
    hass.states.async_set("person.sam", "home", {"friendly_name": "Sam"})
    hass.states.async_set("weather.home", "cloudy", {"temperature": 61, "temperature_unit": "°F"})
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
    """A tool's result data: Home Assistant returns a ToolResult on newer versions and a plain dict on
    older ones.
    """
    return getattr(result, "data", result)


async def test_entities(hass: HomeAssistant) -> None:
    """Her three feelings and her memory appear as entities, with sensible starting states."""
    await setup(hass)
    mood = hass.states.get("select.lena_mood")
    assert mood is not None and mood.state in mood.attributes["options"]
    assert "Grumpy" in mood.attributes["options"]
    assert hass.states.get("select.lena_energy").state in ("Steady", "Groggy", "Wired", "Drained")
    assert hass.states.get("select.lena_moment").state == "Nothing"
    assert hass.states.get("todo.lena_memory").state == "0"


async def test_set_by_hand_clears_reason(hass: HomeAssistant) -> None:
    """Picking a mood by hand clears the old reason, which no longer explains it."""
    await setup(hass)
    core = hass.data[DOMAIN]
    core.feelings.mood_reason = "It's raining."
    await hass.services.async_call("select", "select_option", {"entity_id": "select.lena_mood", "option": "Grumpy"}, blocking=True)
    state = hass.states.get("select.lena_mood")
    assert state.state == "Grumpy"
    assert state.attributes.get("reason") is None


async def test_llm_api_prompt_and_tools(hass: HomeAssistant) -> None:
    """The LLM API gives her prompt only her current feelings and her memory, and the three tools work
    end to end.
    """
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
    """The feel action sets a moment, and it fades back to Nothing after its time."""
    await setup(hass)
    await hass.services.async_call(DOMAIN, "feel", {"moment": "Amused", "reason": "A good pun."}, blocking=True)
    assert hass.states.get("select.lena_moment").state == "Amused"
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=21))
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_moment").state == "Nothing"


async def test_memory_todo_list(hass: HomeAssistant) -> None:
    """Her memory works as a to-do list: add, tick off (retired from her prompt), remove."""
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
    """A leak worries her only after two minutes, keeps her worried past the usual fade, and relieves
    her when it clears.
    """
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
    """Someone back after more than a day: a Delighted moment that names them, and a Fond mood."""
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
    """A mood that has lasted long enough drifts on the hour (with the change chance at 100%)."""
    await setup(hass)
    core = hass.data[DOMAIN]
    core.feelings.mood_since = dt_util.now() - timedelta(hours=5)
    before = core.feelings.mood
    core._on_hour(dt_util.now())  # what the hourly timer calls
    await hass.async_block_till_done()
    assert hass.states.get("select.lena_mood").state != before


async def test_no_sunshine_after_dark(hass: HomeAssistant) -> None:
    """A weather service that still says "sunny" at night gets a clear night, not "the sun came out"."""
    await setup(hass)
    hass.states.async_set("sun.sun", "below_horizon")
    hass.states.async_set("weather.home", "sunny", {"temperature": 50, "temperature_unit": "°F"})
    await hass.async_block_till_done()
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=16))
    await hass.async_block_till_done()
    reason = hass.states.get("select.lena_mood").attributes.get("reason")
    assert reason in (None, "The sky just cleared.")


async def test_sunset_is_not_news(hass: HomeAssistant) -> None:
    """Going from sunny to a clear night at sunset doesn't count as the weather turning."""
    await setup(hass)
    core = hass.data[DOMAIN]
    hass.states.async_set("sun.sun", "above_horizon")
    hass.states.async_set("weather.home", "sunny", {"temperature": 60})
    await hass.async_block_till_done()
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=16))
    await hass.async_block_till_done()
    core.feelings.mood_reason = "keep"
    hass.states.async_set("sun.sun", "below_horizon")
    hass.states.async_set("weather.home", "clear-night", {"temperature": 55})
    await hass.async_block_till_done()
    async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=40))
    await hass.async_block_till_done()
    assert core.feelings.mood_reason == "keep"


@pytest.mark.parametrize(
    ("sun", "weather", "reason", "expected"),
    [
        ("below_horizon", "cloudy", "The sun just came out.", None),  # no longer true: dropped
        ("below_horizon", "sunny", "The sun just came out.", "It's a clear night."),
        ("above_horizon", "rainy", "It just started raining.", "It's raining."),  # news becomes how it is
        ("above_horizon", "rainy", "It's raining.", "It's raining."),
    ],
)
async def test_mood_reason_stays_true(hass: HomeAssistant, sun, weather, reason, expected) -> None:
    """Each hour her mood's reason is checked: news becomes how things are, and what's no longer true
    is dropped, while the mood stays.
    """
    await setup(hass, dict(OPTIONS, mood_change_chance=0))
    core = hass.data[DOMAIN]
    hass.states.async_set("sun.sun", sun)
    hass.states.async_set("weather.home", weather, {"temperature": 60})
    core.feelings.mood, core.feelings.mood_reason = "Playful", reason
    core.feelings.mood_since = dt_util.now() - timedelta(hours=1, minutes=5)
    core._changed()
    core._on_hour(dt_util.now())
    await hass.async_block_till_done()
    mood = hass.states.get("select.lena_mood")
    assert mood.state == "Playful"  # the mood lingers; only its reason changes
    assert mood.attributes.get("reason") == expected


async def test_context_view(hass: HomeAssistant, hass_client) -> None:
    """The context view returns her context text and the parts the app reads, weather included."""
    await setup(hass)
    client = await hass_client()
    response = await client.get("/api/lena/context")
    assert response.status == 200
    body = await response.json()
    assert "## How you feel right now" in body["context"]
    assert set(body["feelings"]) == {"mood", "mood_reason", "energy", "moment", "moment_reason"}
    assert body["memory"] == []
    assert body["weather"] == {"condition": "cloudy", "temperature": 61, "unit": "°F"}


async def test_devices_in_context(hass: HomeAssistant, hass_client) -> None:
    """Open WebUI's house tools match names exactly, so she's given the exact names by room."""
    from homeassistant.components.homeassistant.exposed_entities import async_expose_entity
    from homeassistant.helpers import area_registry as ar, entity_registry as er
    from homeassistant.setup import async_setup_component

    assert await async_setup_component(hass, "homeassistant", {})
    await setup(hass)
    living = ar.async_get(hass).async_create("Living Room")
    reg = er.async_get(hass)
    entry = reg.async_get_or_create("cover", "test", "curtains", suggested_object_id="living_room_curtains")
    reg.async_update_entity(entry.entity_id, area_id=living.id, aliases={"Curtains"})
    hass.states.async_set(entry.entity_id, "closed", {"friendly_name": "Living Room Curtains"})
    hass.states.async_set("cover.garage_door", "closed", {"friendly_name": "Garage Door"})
    hass.states.async_set("light.secret", "off", {"friendly_name": "Secret Lamp"})
    async_expose_entity(hass, "conversation", entry.entity_id, True)
    async_expose_entity(hass, "conversation", "cover.garage_door", True)
    async_expose_entity(hass, "conversation", "light.secret", False)
    hass.states.async_set("calendar.personal", "off", {"friendly_name": "Personal calendar"})
    async_expose_entity(hass, "conversation", "calendar.personal", True)

    client = await hass_client()
    body = await (await client.get("/api/lena/context?devices=1")).json()
    text = body["context"]
    assert "## Your home" in text
    assert "- Living Room: cover: Living Room Curtains (also Curtains)" in text
    assert "- No room: calendar: Personal calendar; cover: Garage Door" in text  # calendars too: their tool wants the name
    assert "Secret Lamp" not in text  # not exposed to Assist
    assert "closed" not in text.split("## Your home")[1]  # names only, no states
    plain = (await (await client.get("/api/lena/context")).json())["context"]
    assert "## Your home" not in plain


async def test_time_of_day_in_context(hass: HomeAssistant, freezer) -> None:
    """She's told the part of the day and whether it's dark, so 8 PM isn't sunny."""
    await hass.config.async_set_time_zone("America/New_York")
    freezer.move_to("2026-10-31 00:05:00+00:00")  # 8:05 PM in New York
    await setup(hass)
    hass.states.async_set("sun.sun", "below_horizon", {"next_rising": "2026-10-31T11:16:00+00:00"})
    text = hass.data[DOMAIN].context()
    assert "8:05 PM: evening, and dark out (the sun rises at 7:16 AM)." in text


async def test_options_flow(hass: HomeAssistant) -> None:
    """Configure saves the options it's given."""
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
    """Lena can only be added once."""
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": "user"})
    assert result["type"] == "form"
    result = await hass.config_entries.flow.async_configure(result["flow_id"], user_input={})
    assert result["type"] == "create_entry" and result["title"] == "Lena"
    result = await hass.config_entries.flow.async_init(DOMAIN, context={"source": "user"})
    assert result["type"] == "abort"


async def _leak(hass: HomeAssistant, state: str) -> None:
    """Turns the leak sensor on or off and lets two minutes pass, so a worry (or its end) registers."""
    hass.states.async_set("binary_sensor.leak", state, {"friendly_name": "Leak sensor", "device_class": "moisture"})
    await hass.async_block_till_done()
    if state == "on":
        async_fire_time_changed(hass, dt_util.utcnow() + timedelta(minutes=3))
        await hass.async_block_till_done()


async def test_alerts_plain(hass: HomeAssistant) -> None:
    """Without an agent, the phones get the plain reason, then an all-clear."""
    calls = async_mock_service(hass, "notify", "mobile_app_pixel")
    await setup(hass, options=dict(OPTIONS, alert_phones=["notify.mobile_app_pixel"]))
    await _leak(hass, "on")
    assert len(calls) == 1
    assert calls[0].data["title"] == "Lena"
    assert calls[0].data["message"] == "Leak sensor is detecting water."
    assert calls[0].data["data"]["tag"] == "lena-alert"
    assert "io.github.soullink9.lena" in calls[0].data["data"]["clickAction"]
    await _leak(hass, "off")
    assert len(calls) == 2
    assert calls[1].data["message"] == "Leak sensor is back to normal."


async def test_alerts_in_her_words_and_no_all_clear(hass: HomeAssistant) -> None:
    """With an agent, the alert is in her words, and with the all-clear off only the alert is sent."""
    calls = async_mock_service(hass, "notify", "mobile_app_pixel")
    options = dict(OPTIONS, alert_phones=["notify.mobile_app_pixel"], alert_agent="conversation.lena", alert_all_clear=False)
    await setup(hass, options=options)
    asked = async_mock_service(
        hass, "conversation", "process",
        response={"response": {"response_type": "action_done",
                               "speech": {"plain": {"speech": "Water under the sink. Go look, now."}}}},
        supports_response=SupportsResponse.ONLY,
    )
    await _leak(hass, "on")
    assert asked[0].data["agent_id"] == "conversation.lena"
    assert "Leak sensor is detecting water." in asked[0].data["text"]
    assert calls[0].data["message"] == "Water under the sink. Go look, now."
    await _leak(hass, "off")
    assert len(calls) == 1  # no all-clear


async def test_no_alerts_without_phones(hass: HomeAssistant) -> None:
    """No phones chosen: no notifications at all."""
    calls = async_mock_service(hass, "notify", "mobile_app_pixel")
    await setup(hass)
    await _leak(hass, "on")
    assert calls == []


async def test_media_view(hass: HomeAssistant, hass_client, tmp_path) -> None:
    """The media view lists a folder and its photos, using a local media folder in place of Immich."""
    from homeassistant.setup import async_setup_component

    from custom_components.lena.view import _screen_sized

    (tmp_path / "beach.jpg").write_bytes(b"\xff\xd8\xff")
    (tmp_path / "dog.png").write_bytes(b"\x89PNG")
    (tmp_path / "notes.txt").write_text("not a photo")
    (tmp_path / "2025").mkdir()
    hass.config.media_dirs = {"local": str(tmp_path)}
    assert await async_setup_component(hass, "media_source", {})
    await setup(hass)
    client = await hass_client()

    top = await (await client.get("/api/lena/media")).json()
    assert any(c["folder"] for c in top["children"])

    folder = await client.get("/api/lena/media", params={"id": "media-source://media_source/local/."})
    assert folder.status == 200
    children = (await folder.json())["children"]
    titles = {c["title"]: c for c in children}
    assert titles["beach.jpg"]["photo"] and titles["dog.png"]["photo"]
    assert titles["2025"]["folder"]
    assert "notes.txt" not in titles

    photos = await (await client.get(
        "/api/lena/media", params={"id": "media-source://media_source/local/.", "photos": "1"})).json()
    assert sorted(photos["photos"]) == ["/media/local/beach.jpg", "/media/local/dog.png"]

    bad = await client.get("/api/lena/media", params={"id": "media-source://nothing_here"})
    assert bad.status == 400

    assert _screen_sized("/immich/abc/123/fullsize/image/heic") == "/immich/abc/123/preview/image/jpeg"
    assert _screen_sized("/media/local/beach.jpg") == "/media/local/beach.jpg"


async def _calendars(hass: HomeAssistant):
    """Two calendars that take new events (Personal exposed to Assist, Work not) and a read-only one
    that's exposed, with calendar.create_event recorded instead of run.
    """
    from homeassistant.components.homeassistant.exposed_entities import async_expose_entity
    from homeassistant.setup import async_setup_component

    assert await async_setup_component(hass, "homeassistant", {})
    hass.states.async_set("calendar.personal", "off", {"friendly_name": "Personal", "supported_features": 7})
    hass.states.async_set("calendar.work", "off", {"friendly_name": "Work", "supported_features": 1})
    hass.states.async_set("calendar.holidays", "off", {"friendly_name": "Holidays", "supported_features": 0})
    async_expose_entity(hass, "conversation", "calendar.personal", True)
    async_expose_entity(hass, "conversation", "calendar.work", False)
    async_expose_entity(hass, "conversation", "calendar.holidays", True)
    return async_mock_service(hass, "calendar", "create_event")


async def test_add_calendar_event_tool(hass: HomeAssistant) -> None:
    """Home Assistant's own tools only read calendars; her add_calendar_event tool writes through
    calendar.create_event, to exposed calendars that take events, in the house's time zone.
    """
    calls = await _calendars(hass)
    await setup(hass)
    api = await llm.async_get_api(hass, "lena", context())
    assert "add_calendar_event" in {t.name for t in api.tools}

    result = await api.async_call_tool(llm.ToolInput(
        tool_name="add_calendar_event",
        tool_args={"calendar": "personal", "title": "Dentist", "start": "2026-11-14T15:30", "location": "Main St"},
    ))
    assert data(result)["added"] == "Dentist" and data(result)["calendar"] == "Personal"
    call = calls[-1].data
    assert call["entity_id"] == "calendar.personal" and call["summary"] == "Dentist" and call["location"] == "Main St"
    start = dt_util.parse_datetime(call["start_date_time"])
    end = dt_util.parse_datetime(call["end_date_time"])
    assert start.tzinfo is not None and start.hour == 15 and end - start == timedelta(hours=1)  # an hour by default

    # All day: the end is the last day, stored as the day after.
    await api.async_call_tool(llm.ToolInput(
        tool_name="add_calendar_event",
        tool_args={"calendar": "Personal", "title": "Trip", "start": "2026-11-20", "end": "2026-11-22"},
    ))
    assert calls[-1].data["start_date"] == "2026-11-20" and calls[-1].data["end_date"] == "2026-11-23"

    # A mistake comes back as words she can act on, and nothing is added.
    count = len(calls)
    result = await api.async_call_tool(llm.ToolInput(
        tool_name="add_calendar_event",
        tool_args={"calendar": "Personal", "title": "Call", "start": "2026-11-14T15:30", "end": "2026-11-14T15:00"},
    ))
    assert data(result)["added"] is None and "after the start" in data(result)["error"]
    assert len(calls) == count


async def test_add_event_action(hass: HomeAssistant) -> None:
    """lena.add_event, which Open WebUI's tool calls over REST: the same rules, a response saying what
    was added, and a clear error for a calendar she can't use.
    """
    from homeassistant.exceptions import ServiceValidationError

    calls = await _calendars(hass)
    await setup(hass)
    # The only writable exposed calendar can be left out.
    reply = await hass.services.async_call(
        DOMAIN, "add_event", {"title": "Bins out", "start": "2026-11-16"}, blocking=True, return_response=True,
    )
    assert reply["calendar"] == "Personal" and calls[-1].data["end_date"] == "2026-11-17"

    for calendar in ("Work", "Holidays"):  # not exposed; read-only
        with pytest.raises(ServiceValidationError, match="Use one of these names exactly: Personal"):
            await hass.services.async_call(
                DOMAIN, "add_event", {"calendar": calendar, "title": "x", "start": "2026-11-16"}, blocking=True,
            )


async def test_displays_rooms_and_announcements(hass: HomeAssistant, hass_client) -> None:
    """The app registers as a display, put in a room it learns that room and its speakers (Music
    Assistant's first), lena.announce reaches it through its Announcement, and it can be deleted.
    """
    from homeassistant.exceptions import ServiceValidationError
    from homeassistant.helpers import area_registry as ar, device_registry as dr, entity_registry as er

    entry = await setup(hass)
    client = await hass_client()

    # Bad ids are refused; a good one becomes a device with an Announcement.
    assert (await client.post("/api/lena/display", json={"id": "../x", "name": "Kitchen tablet"})).status == 400
    response = await client.post("/api/lena/display", json={"id": "abc-123", "name": "Kitchen tablet"})
    assert response.status == 200
    first = await response.json()
    assert first["name"] == "Kitchen tablet" and first["area"] is None and first["speakers"] == []
    assert first["announce_entity"] and first["announce_entity"].startswith("event.")
    device = dr.async_get(hass).async_get(first["device_id"])
    assert device is not None and entry.entry_id in device.config_entries

    # Put in the kitchen, with two speakers there and one elsewhere.
    kitchen = ar.async_get(hass).async_create("Kitchen")
    dr.async_get(hass).async_update_device(device.id, area_id=kitchen.id)
    reg = er.async_get(hass)
    for platform, object_id, area in (("cast", "kitchen_cast", kitchen.id), ("music_assistant", "kitchen_ma", kitchen.id),
                                      ("cast", "den_cast", None)):
        e = reg.async_get_or_create("media_player", platform, object_id, suggested_object_id=object_id)
        reg.async_update_entity(e.entity_id, area_id=area)
        hass.states.async_set(e.entity_id, "idle", {"friendly_name": object_id.replace("_", " ").title()})
    again = await (await client.post("/api/lena/display", json={"id": "abc-123", "name": "Kitchen tablet"})).json()
    assert again["device_id"] == first["device_id"] and again["announce_entity"] == first["announce_entity"]
    assert again["area"] == {"id": kitchen.id, "name": "Kitchen"}
    assert [s["entity_id"] for s in again["speakers"]] == ["media_player.kitchen_ma", "media_player.kitchen_cast"]
    assert again["speakers"][0]["music_assistant"] is True

    # An announcement fires the display's event with the message.
    entity = first["announce_entity"]
    await hass.services.async_call(DOMAIN, "announce", {"message": "Dinner's ready!", "listen": True}, blocking=True)
    await hass.async_block_till_done()
    state = hass.states.get(entity)
    assert state.attributes["event_type"] == "announce"
    assert state.attributes["message"] == "Dinner's ready!" and state.attributes["listen"] is True
    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(DOMAIN, "announce", {"message": "x", "device_id": ["nope"]}, blocking=True)

    # It survives a restart of the integration, and only displays can be deleted.
    assert await hass.config_entries.async_reload(entry.entry_id)
    await hass.async_block_till_done()
    assert hass.states.get(entity) is not None
    from custom_components.lena import async_remove_config_entry_device
    assert await async_remove_config_entry_device(hass, entry, device)
    lena = dr.async_get(hass).async_get_device(identifiers={(DOMAIN, "lena")})
    assert lena is not None and not await async_remove_config_entry_device(hass, entry, lena)
