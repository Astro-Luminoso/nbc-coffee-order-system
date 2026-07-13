# Dev-local
> This document explains how to run the project locally and what local infrastructure is required.

## Dev Tools
- Java 21
- Docker
- Gradle

## Local Infrastructure

The local profile uses an embedded H2 database and Redis running in Docker.
Start Redis with the following command:

```bash
docker run --name coffee-order-redis --detach --publish 6379:6379 redis:7.4-alpine
```

If the container already exists, start it with:

```bash
docker start coffee-order-redis
```

Verify the Redis connection before running the application:

```bash
docker exec coffee-order-redis redis-cli ping
```

The expected response is `PONG`.

| Service | Runtime              | Open port   | Purpose                             |
|---------|----------------------|-------------|-------------------------------------|
| H2      | Embedded application | N/A         | Local application database          |
| Redis   | `redis:7.4-alpine`   | `6379:6379` | Popular-menu projection             |

If the Redis connection fails, print `Application failed due to local infrastructure connection error` in the console and stop the session.

## Local Application Configuration

Use `application-local.yml` for the local environment. This file is created at the following path and is not included in Git:

```text
src/main/resources/application-local.yml
```

Run the application with the local profile enabled:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

The H2 database is in memory and is reset whenever the application stops. The H2 console is available at `http://localhost:8080/h2-console` while the application is running. Use `jdbc:h2:mem:coffee-order;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE` as the JDBC URL.
