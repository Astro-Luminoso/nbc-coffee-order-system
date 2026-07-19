# Local Development

## Prerequisites

- Java 21
- Gradle
- Docker
- A MockAPI.io project for manual external-integration testing

## Local Infrastructure

The local profile uses an embedded H2 database and Redis. Redis provides the
daily ZSET cache used by the popular-menu API. MockAPI.io is a hosted external
dependency used only when the real collection adapter is enabled for manual
local testing.

Start Redis with the following command:

```bash
docker run --name coffee-order-redis --detach --publish 6379:6379 redis:7.4-alpine
```

If the container already exists, start it with:

```bash
docker start coffee-order-redis
```

Verify the Redis connection:

```bash
docker exec coffee-order-redis redis-cli ping
```

The expected response is `PONG`.

| Service | Runtime | Purpose |
|---|---|---|
| H2 | Embedded application database | Local development and tests |
| Redis | `redis:7.4-alpine` | Daily popular-menu ZSET cache |
| MockAPI.io | Hosted REST API | Collected paid-order records for manual integration testing |

## MockAPI.io Project Setup

Create one MockAPI.io project and an `orders` resource. Configure the resource
with these application-owned fields. The project and resource workflow follows
the [MockAPI.io quick-start guide](https://github.com/mockapi-io/docs/wiki/Quick-start-guide).

| Field | MockAPI.io type | Meaning |
|---|---|---|
| `orderId` | Number | Local paid-order identifier and reconciliation key |
| `userId` | Number | Local user identifier |
| `menuId` | Number | Local menu identifier |
| `paymentAmount` | Number | Points deducted for the order |

MockAPI.io adds its own `id` to each resource. The application does not send or
persist that value. Copy the project base URL including its API prefix, for
example `https://<project-token>.mockapi.io/api/v1`, but excluding `/orders`.
Do not commit the real project URL.

## Local Application Configuration

Use `application-local.yml` for local-only settings. The file is not committed
to Git.

Set the MockAPI.io project URL before starting the application:

```bash
export MOCKAPI_BASE_URL='https://<project-token>.mockapi.io/api/v1'
```

Optional timeout overrides use ISO 8601 durations:

```bash
export MOCKAPI_CONNECT_TIMEOUT='PT2S'
export MOCKAPI_READ_TIMEOUT='PT5S'
```

The application appends `/orders` to the configured base URL. It first requests
`GET /orders?orderId={orderId}` and creates a record with `POST /orders` only
when lookup returns an empty array.

Run the application with the local profile enabled:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The H2 database is in memory and is reset whenever the application stops. The
H2 console is available at `http://localhost:8080/h2-console` while the
application is running. Use
`jdbc:h2:mem:coffee-order;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
as the JDBC URL.

Automated tests must not call the live MockAPI.io project. Client-level tests
use a local HTTP stub, while application-service tests replace
`DataCollectionClient` with a test double.
