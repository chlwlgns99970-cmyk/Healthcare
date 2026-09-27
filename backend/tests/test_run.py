from __future__ import annotations

import pytest

from app.run import resolve_port


@pytest.mark.parametrize(("value", "expected"), [(None, 8000), ("8001", 8001), ("65535", 65535)])
def test_resolve_port(value, expected):
    assert resolve_port(value) == expected


@pytest.mark.parametrize("value", ["0", "65536", "invalid"])
def test_resolve_port_rejects_invalid_values(value):
    with pytest.raises(ValueError, match="PORT"):
        resolve_port(value)
