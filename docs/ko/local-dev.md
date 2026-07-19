# 로컬 개발 환경

> 원문: [English Local Development Guide](../local-dev.md)

## 사전 요구 사항

- Java 21
- Gradle
- Docker
- 수동 외부 연동 테스트용 MockAPI.io 프로젝트

## 로컬 인프라

로컬 프로필은 내장 H2 데이터베이스와 Redis를 사용합니다. Redis는 인기 메뉴 API가 사용하는
일별 ZSET 캐시를 제공합니다. MockAPI.io는 실제 수집 어댑터를 활성화해 수동 로컬 테스트를 할
때만 사용하는 호스팅 외부 의존성입니다.

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
| MockAPI.io | 호스팅 REST API | 수동 연동 테스트용 결제 주문 수집 레코드 |

## MockAPI.io 프로젝트 설정

MockAPI.io 프로젝트 하나와 `orders` 리소스를 생성합니다. 리소스에는 애플리케이션이 관리하는
다음 필드를 설정합니다. 프로젝트와 리소스 생성 방식은
[MockAPI.io 빠른 시작 가이드](https://github.com/mockapi-io/docs/wiki/Quick-start-guide)를
따릅니다.

| 필드 | MockAPI.io 타입 | 의미 |
|---|---|---|
| `orderId` | Number | 로컬 결제 주문 식별자이자 조정 키 |
| `userId` | Number | 로컬 사용자 식별자 |
| `menuId` | Number | 로컬 메뉴 식별자 |
| `paymentAmount` | Number | 주문에서 차감한 포인트 |

MockAPI.io는 각 리소스에 자체 `id`를 추가합니다. 애플리케이션은 이 값을 보내거나 저장하지
않습니다. `/orders`를 제외하고 API prefix까지 포함한 프로젝트 Base URL을 복사합니다. 예를 들면
`https://<project-token>.mockapi.io/api/v1`입니다. 실제 프로젝트 URL은 커밋하지 않습니다.

## 로컬 애플리케이션 설정

로컬 전용 설정에는 `application-local.yml`을 사용합니다. 이 파일은 Git에 커밋하지 않습니다.

애플리케이션을 실행하기 전에 MockAPI.io 프로젝트 URL을 설정합니다.

```bash
export MOCKAPI_BASE_URL='https://<project-token>.mockapi.io/api/v1'
```

타임아웃은 ISO 8601 duration으로 선택적으로 변경할 수 있습니다.

```bash
export MOCKAPI_CONNECT_TIMEOUT='PT2S'
export MOCKAPI_READ_TIMEOUT='PT5S'
```

애플리케이션은 설정한 Base URL에 `/orders`를 덧붙입니다. 먼저
`GET /orders?orderId={orderId}`를 호출하고 조회 결과가 빈 배열일 때만 `POST /orders`로
레코드를 생성합니다.

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

자동화 테스트는 실제 MockAPI.io 프로젝트를 호출하지 않습니다. 클라이언트 수준 테스트는 로컬
HTTP 스텁을 사용하고 애플리케이션 서비스 테스트는 `DataCollectionClient`를 테스트 대역으로
교체합니다.
