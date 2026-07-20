# k6 Load Tests

These scripts run k6 from Docker against an application running on the host.
They are intended for local development only. Do not use them against a shared
or production environment without an agreed test plan.

## Prerequisites

1. Start Redis and the application.

   ```bash
   docker run --name coffee-order-redis --detach --publish 6379:6379 redis:7.4-alpine
   ./gradlew bootRun
   ```

2. Docker Desktop must be running. The scripts use
   `host.docker.internal:8080`, which resolves from a Docker Desktop container
   to the application on the host.

The local seed includes users with IDs `1` through `5` and coffee menus with
IDs `1` through `5`.

## Read API load test

This test sends 50 requests per second to the menu API and 20 requests per
second to the popular-menu API for one minute.

```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  grafana/k6 run - < k6/read-api.js
```

## Charge and payment flow load test

Every iteration generates new idempotency keys, charges user `5`, and creates
one paid order. The default 100-iteration-per-second rate intentionally
creates a hot-user contention scenario; lower it in `write-api.js` before
using the script as a smoke test.

```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e USER_ID=5 \
  -e MENU_ID=1 \
  grafana/k6 run - < k6/write-api.js
```

Configure a MockAPI-compatible test endpoint before using this script for
performance measurements. Without `MOCKAPI_BASE_URL`, each payment still
commits, but the collection delivery attempt fails and produces retry work
that distorts the result.

## Concurrent idempotency test

This scenario sends 20 concurrent payment requests with exactly the same
idempotency key. It checks that every response has the same remaining balance,
which demonstrates that only one point deduction occurred.

```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -e USER_ID=5 \
  -e MENU_ID=1 \
  grafana/k6 run - < k6/idempotency-replay.js
```

Run this scenario by itself because it calculates its expected balance from
the selected user's current balance.

## Result interpretation

- `http_req_duration`: latency distribution; focus on `p(95)`.
- `http_req_failed`: transport-level failures.
- `checks`: API contract and business-result assertions.
- `dropped_iterations`: the requested arrival rate could not be sustained.

The popular-menu API intentionally excludes orders created on the current
business date. A payment produced during these tests therefore does not appear
in the popular-menu result until a later completed-date period.
