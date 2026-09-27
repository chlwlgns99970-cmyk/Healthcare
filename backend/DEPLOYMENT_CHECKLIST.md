# Food photo analysis deployment checklist

## Secrets and model access

- Store `OPENAI_API_KEY` only in the hosting platform's secret manager.
- Set `OPENAI_MODEL=gpt-5.6-terra` in the server environment; do not put either value in the APK.
- Run `GET /ready` and one real non-sensitive integration image to verify the project can access the configured model.
- Confirm provider budgets, spend alerts, data controls, and retention requirements for the deployed project.

## Network and runtime

- Deploy `backend/Dockerfile` to a managed service that injects `PORT` and terminates TLS on a public HTTPS domain.
- Configure the readiness probe as `GET /ready` and the liveness probe as `GET /health`.
- Put Uvicorn behind the platform proxy; do not expose it directly.
- Set `FOOD_ANALYSIS_FORWARDED_ALLOW_IPS` to the platform's exact trusted proxy addresses. Never use `*`.
- Configure WAF/request-size limits, timeouts, TLS renewal, structured-log redaction, monitoring, and alerts.
- Replace the in-process rate limiter and idempotency store with a shared Redis/database implementation before scaling beyond one process.
- Decide and document endpoint protection. There is currently no user authentication, so the endpoint must not be described as private.

## Android release and physical-device acceptance

- Build with `FOOD_ANALYSIS_BASE_URL=https://<public-domain>/`; Release validation rejects local and private hosts.
- Scan the APK for API keys, bearer tokens, user photos, and unintended endpoint credentials.
- On a physical device, photograph one food and one non-food scene; verify success/uncertain/no-food and retry/error paths.
- Edit item name, amount, and calories; save once; verify the dashboard total and persisted history after process restart.
- Repeat the same request to verify idempotency, then test rate-limit and upstream timeout messages without leaking provider details.
- Record the APK SHA-256, backend release identifier, model environment value, test time, device/OS, and final pass/fail evidence.

## Local calorie evaluation (do not commit photos)

- Keep user-approved photos outside the repository and strip location metadata before use.
- Record only a local identifier, reference kcal/range, predicted kcal/range, absolute error, percent error, status, warnings, and reviewer notes.
- Summarize mean absolute error and large-error cases separately; do not present photo-based estimates as measurements or medical advice.
