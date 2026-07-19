# 로컬 개발 환경

> 원문: [English Local Development Guide](../local-dev.md)

## 사전 요구 사항

- Java 21
- Gradle
- Docker

## 로컬 인프라

로컬 프로필은 내장 H2 데이터베이스와 Redis를 사용합니다. Redis는 인기 메뉴 API가 사용하는
일별 ZSET 캐시를 제공합니다.

다음 명령으로 Redis를 시작합니다.

```bash
docker run --name coffee-order-redis --detach --publish 6379:6379 redis:7.4-alpine
```

컨테이너가 이미 있으면 다음 명령으로 시작합니다.

```bash
docker start coffee-order-redis
```

Redis 연결을 확인합니다.

```bash
docker exec coffee-order-redis redis-cli ping
```

예상 응답은 `PONG`입니다.

| 서비스 | 런타임 | 용도 |
|---|---|---|
| H2 | 애플리케이션 내장 데이터베이스 | 로컬 개발과 테스트 |
| Redis | `redis:7.4-alpine` | 일별 인기 메뉴 ZSET 캐시 |

## 로컬 애플리케이션 설정

로컬 전용 설정에는 `application-local.yml`을 사용합니다. 이 파일은 Git에 커밋하지 않습니다.

로컬 프로필을 활성화해 애플리케이션을 실행합니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

H2 데이터베이스는 메모리에 존재하므로 애플리케이션이 종료되면 초기화됩니다. 실행 중에는
`http://localhost:8080/h2-console`에서 H2 콘솔을 사용할 수 있습니다. JDBC URL에는 아래 값을
사용합니다.

```text
jdbc:h2:mem:coffee-order;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
```
