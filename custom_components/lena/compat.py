"""Small compatibility shims across Home Assistant versions.

Home Assistant 2026.10 replaced voluptuous with probatio, a drop-in fork. Schemas must use
whichever one this Home Assistant uses, so take it from Home Assistant itself.
"""

from homeassistant.helpers import config_validation as cv
from homeassistant.helpers import llm

vol = getattr(cv, "probatio", None) or getattr(cv, "vol")

# Tools return a ToolResult from 2026.10 on; earlier versions take a plain dict.
_ToolResult = getattr(llm, "ToolResult", None)


def tool_result(data: dict):
    """Wrap a tool's answer the way this Home Assistant expects."""
    return _ToolResult(data=data) if _ToolResult is not None else data
