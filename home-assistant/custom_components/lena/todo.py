"""Her memory, as a to-do list (todo.lena_memory) you can read and edit like any other.

Ticking an item off retires it: she only uses the open ones.
"""

from __future__ import annotations

from homeassistant.components.todo import TodoItem, TodoItemStatus, TodoListEntity, TodoListEntityFeature
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback

from .const import SIGNAL_UPDATED
from .core import LenaCore
from .select import device_info


async def async_setup_entry(hass: HomeAssistant, entry, async_add_entities: AddConfigEntryEntitiesCallback) -> None:
    """Her memory as a to-do list."""
    async_add_entities([LenaMemory(entry.runtime_data)])


class LenaMemory(TodoListEntity):
    """Lena Memory: what she remembers, as a to-do list you can read and edit in Home Assistant. Each
    item is one fact; ticking it off retires it from her prompt without deleting it.
    """
    _attr_has_entity_name = True
    _attr_name = "Memory"
    _attr_icon = "mdi:head-heart-outline"
    _attr_unique_id = "lena_memory"
    _attr_should_poll = False
    _attr_supported_features = (
        TodoListEntityFeature.CREATE_TODO_ITEM
        | TodoListEntityFeature.UPDATE_TODO_ITEM
        | TodoListEntityFeature.DELETE_TODO_ITEM
        | TodoListEntityFeature.MOVE_TODO_ITEM
    )

    def __init__(self, core: LenaCore) -> None:
        """Reads the list from the core, which owns and saves it."""
        self._core = core
        self._attr_device_info = device_info()
        self._refresh()

    def _refresh(self) -> None:
        """Copies the core's list into the entity's items."""
        self._attr_todo_items = [
            TodoItem(summary=m["summary"], uid=m["uid"], status=TodoItemStatus(m.get("status", "needs_action")))
            for m in self._core.memory
        ]

    async def async_create_todo_item(self, item: TodoItem) -> None:
        """Adding an item is telling her to remember it."""
        self._core.remember(item.summary or "")

    async def async_update_todo_item(self, item: TodoItem) -> None:
        """Editing an item: its text, or ticking it off (which retires it from her prompt)."""
        for m in self._core.memory:
            if m["uid"] == item.uid:
                if item.summary is not None:
                    m["summary"] = item.summary
                if item.status is not None:
                    m["status"] = str(item.status)
        self._core.memory_changed()

    async def async_delete_todo_items(self, uids: list[str]) -> None:
        """Deleting items forgets them for good."""
        self._core.memory[:] = [m for m in self._core.memory if m["uid"] not in uids]
        self._core.memory_changed()

    async def async_move_todo_item(self, uid: str, previous_uid: str | None = None) -> None:
        """Reordering, by drag and drop in Home Assistant; the order is also the order in her prompt."""
        items = self._core.memory
        moving = next((m for m in items if m["uid"] == uid), None)
        if moving is None:
            return
        items.remove(moving)
        at = 0 if previous_uid is None else next((i + 1 for i, m in enumerate(items) if m["uid"] == previous_uid), len(items))
        items.insert(at, moving)
        self._core.memory_changed()

    async def async_added_to_hass(self) -> None:
        """Follow the core's changes for as long as the entity exists."""
        self.async_on_remove(async_dispatcher_connect(self.hass, SIGNAL_UPDATED, self._updated))

    @callback
    def _updated(self) -> None:
        """The memory changed (she remembered or forgot something): refresh and write the state."""
        self._refresh()
        self.async_write_ha_state()
