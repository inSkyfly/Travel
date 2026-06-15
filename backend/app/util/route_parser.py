"""从自然语言中解析出发地 / 目的地（如「成都到新疆」「成都--新疆」）。"""

from __future__ import annotations

import re

_ROUTE_PATTERNS = [
  re.compile(
      r"从\s*([^，,。；;！!？?\s到至\-—~]+?)\s*(?:到|至|->|—|-|~)\s*([^，,。；;！!？?\s]+)",
  ),
  re.compile(
      r"([^，,。；;！!？?\s]+?)\s*(?:到|至|->)\s*([^，,。；;！!？?\s]+)",
  ),
  re.compile(
      r"([^，,。；;！!？?\s]+?)\s*--\s*([^，,。；;！!？?\s]+)",
  ),
]


def parse_route(text: str) -> tuple[str, str] | None:
    normalized = text.strip()
    if not normalized:
        return None
    for pattern in _ROUTE_PATTERNS:
        m = pattern.search(normalized)
        if not m:
            continue
        origin = m.group(1).strip()
        destination = m.group(2).strip()
        if origin and destination and origin != destination:
            return origin, destination
    return None
