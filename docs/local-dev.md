# Dev-local
> This document explains how to run the project locally and what local infrastructure is required.

## Dev Tools
- Java 21
- Docker
- Gradle

## Local Infrastructure

Human will run the following components in local environment using Docker.
If any of those connections failed when tried to run the project locally, should leave message "Application failed due to local infrastructure connection error" in console and shut your session.

| Service | Docker img         | open port   | purpose                 |
|---------|--------------------|-------------|-------------------------|
| MySQL   | `mysql:8.4`        | `3306:3306` | 애플리케이션 데이터베이스           |
| Redis   | `redis:7.4-alpine` | `6379:6379` | 검색 캐시와 인기 주문(Top Order) |

## local application configuration
use `application-local.yml` for local environment. This file is created in the following path and is not included in Git.
```text
src/main/resources/application-local.yml
```

Add `src/main/resources/application-local.yml` to `.gitignore` to prevent local environment configuration from being tracked by Git.

