from __future__ import annotations

import os

import uvicorn


def resolve_port(value: str | None) -> int:
    try:
        port = int(value or "8000")
    except ValueError as exc:
        raise ValueError("PORT must be an integer between 1 and 65535") from exc
    if port not in range(1, 65536):
        raise ValueError("PORT must be an integer between 1 and 65535")
    return port


def main() -> None:
    forwarded_allow_ips = os.getenv("FOOD_ANALYSIS_FORWARDED_ALLOW_IPS", "127.0.0.1")
    if forwarded_allow_ips.strip() == "*":
        raise ValueError("FOOD_ANALYSIS_FORWARDED_ALLOW_IPS must list trusted proxy addresses, not '*'")
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=resolve_port(os.getenv("PORT")),
        proxy_headers=True,
        forwarded_allow_ips=forwarded_allow_ips,
    )


if __name__ == "__main__":
    main()
