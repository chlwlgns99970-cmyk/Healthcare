# Food photo analysis backend

This backend is retained for a possible future opt-in automatic-analysis feature.
The current Android photo-recording flow does not upload photos or call this service.

FastAPI server for the Android app's food-photo analysis flow. It validates and
re-encodes JPEG/PNG uploads, calls the OpenAI Responses API from the server, and
returns the Android-compatible structured contract.

## Local setup

From `backend`:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
# Inject OPENAI_API_KEY and OPENAI_MODEL through the process environment or a secret manager.
.\.venv\Scripts\python.exe -m app.run
```

Run tests without a real API call:

```powershell
.\.venv\Scripts\python.exe -m pytest -m "not integration"
```

The server binds `0.0.0.0` and reads the platform-provided `PORT` (default `8000`).
`GET /health` is a liveness check; `GET /ready` returns 503 until both provider
credentials and a model are configured. The integration test runs only when `OPENAI_API_KEY`, `OPENAI_MODEL`, and
`FOOD_ANALYSIS_INTEGRATION_IMAGE` are all set. Never commit `.env` or a user photo.

## Production deployment

Build from this directory with `docker build -t food-photo-analysis .`. Put the
container behind a managed HTTPS reverse proxy/load balancer and a trusted proxy
configuration. Supply secrets through the deployment platform's secret manager.
Do not expose Uvicorn directly to the public internet.

Set `FOOD_ANALYSIS_FORWARDED_ALLOW_IPS` to an explicit comma-separated list of
trusted proxy addresses. The application rejects `*`. Verify the platform's
forwarding topology before using the client IP for rate limiting.

The bundled in-memory rate limiter and idempotency store are suitable for one
development process. Before horizontally scaling production, replace them with
a shared Redis/database implementation. Configure provider project budgets and
alerts, server monitoring, WAF/request-size limits, and consider Play Integrity
verification because this app currently has no user-account authentication.
Follow [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md) before issuing a Release APK.
