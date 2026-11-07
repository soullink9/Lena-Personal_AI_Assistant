import pytest

from homeassistant.setup import async_setup_component

pytest_plugins = "pytest_homeassistant_custom_component"


@pytest.fixture(autouse=True)
def auto_enable_custom_integrations(enable_custom_integrations):
    """Home Assistant only loads custom_components in tests when asked; every test needs Lena."""
    yield


@pytest.fixture(autouse=True)
async def home_assistant_core(hass):
    """Home Assistant's own core integration, always there in a real install: the Assist satellite and
    conversation integrations Lena loads need it (for which entities are exposed to Assist).
    """
    assert await async_setup_component(hass, "homeassistant", {})
    yield
