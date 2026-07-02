# Leaderboard snapshot cron

The Activity screen's `LEADERBOARD_UP` notification and `weeklySpotScore` figure both depend on
a daily snapshot of every user's leaderboard rank/score (`leaderboard_rank_snapshots`, written by
`snapshotAllRanks`). There is no scheduler inside the server process — a snapshot only gets
written when something calls the endpoint below.

## Endpoint

```
POST /api/admin/leaderboard/snapshot/today
Header: X-Cron-Secret: <CRON_SECRET>
```

- Snapshots **today** (`Instant.now()`, resolved in the `LEADERBOARD_SNAPSHOT_ZONE` timezone —
  UTC if unset) — there is no date parameter.
- **Auth:** requires the `X-Cron-Secret` header to exactly match the `CRON_SECRET` environment
  variable. Missing/blank/mismatched secret → `401 Unauthorized`. If `CRON_SECRET` itself isn't
  set on the server, the endpoint always rejects (fails closed).
- **Idempotent:** `snapshotAllRanks` deletes-then-reinserts the current date's rows, so calling
  this endpoint multiple times in the same day (retries, at-least-once schedulers, manual re-runs)
  never duplicates snapshot rows or `LEADERBOARD_UP` activity events.
- **Response:** `200 OK` with `{ "snapshotDate": "2026-07-02", "rowsWritten": 42 }`.
- This is a separate endpoint/secret from the existing human-operated
  `POST /api/admin/leaderboard/snapshot` (`X-Admin-Token`), which is unaffected.

## Environment variables

| Variable | Purpose |
|---|---|
| `CRON_SECRET` | Shared secret the external cron sends in `X-Cron-Secret`. Required for the endpoint to accept any request. |
| `LEADERBOARD_SNAPSHOT_ZONE` | IANA timezone (e.g. `Europe/Bucharest`) used to resolve "today" for the snapshot. Defaults to UTC if unset. |
| `ENABLE_SNAPSHOT_CATCHUP_ON_STARTUP` | Dev-only convenience: when `"true"`, the server backfills today's snapshot once at boot so local Activity testing has data without waiting for a cron call. Defaults to off; **never rely on this in production** — production always depends on the external cron below. |

## Local testing with curl

```bash
# Missing secret → 401
curl -i -X POST http://localhost:8080/api/admin/leaderboard/snapshot/today

# Wrong secret → 401
curl -i -X POST http://localhost:8080/api/admin/leaderboard/snapshot/today \
     -H "X-Cron-Secret: wrong-secret"

# Correct secret → 200 + rowsWritten
curl -i -X POST http://localhost:8080/api/admin/leaderboard/snapshot/today \
     -H "X-Cron-Secret: $CRON_SECRET"

# Idempotency check: running it again the same day should not duplicate rows
curl -i -X POST http://localhost:8080/api/admin/leaderboard/snapshot/today \
     -H "X-Cron-Secret: $CRON_SECRET"
```

## Future: GitHub Actions schedule

Not yet configured — this section documents the intended setup for when it's wired up.

A scheduled workflow at `.github/workflows/leaderboard-snapshot.yml` will run daily and call the
endpoint above:

```yaml
on:
  schedule:
    - cron: "0 2 * * *"   # daily, adjust to land after local midnight in LEADERBOARD_SNAPSHOT_ZONE
  workflow_dispatch: {}    # allow manual runs

jobs:
  snapshot:
    runs-on: ubuntu-latest
    steps:
      - name: Trigger leaderboard snapshot
        run: |
          curl -fsS -X POST "$CARSPOTTER_API_URL/api/admin/leaderboard/snapshot/today" \
               -H "X-Cron-Secret: $CRON_SECRET"
        env:
          CARSPOTTER_API_URL: ${{ secrets.CARSPOTTER_API_URL }}
          CRON_SECRET: ${{ secrets.CRON_SECRET }}
```

`CARSPOTTER_API_URL` and `CRON_SECRET` will be GitHub repository secrets, set separately when the
workflow is actually added. `curl -f` makes the step fail loudly on a non-2xx response (wrong
secret, server down), so a broken cron shows up as a failed workflow run.
