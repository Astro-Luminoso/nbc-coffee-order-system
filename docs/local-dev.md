# Local Development

## Prerequisites

- Java 21
- Gradle
- Docker

## Local Infrastructure

The local profile uses an embedded H2 database and Redis. Redis provides the
daily ZSET cache used by the popular-menu API.

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

## Local Application Configuration

Use `application-local.yml` for local-only settings. The file is not committed
to Git.

Run the application with the local profile enabled:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The H2 database is in memory and is reset whenever the application stops. The
H2 console is available at `http://localhost:8080/h2-console` while the
application is running. Use
`jdbc:h2:mem:coffee-order;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
as the JDBC URL.
