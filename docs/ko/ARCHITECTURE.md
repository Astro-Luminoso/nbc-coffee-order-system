# API 아키텍처

> 원문: [English Architecture](../ARCHITECTURE.md)

## 1. 개요

애플리케이션은 단순한 계층형 아키텍처를 사용합니다. 컨트롤러는 HTTP 관심사를 처리하고,
애플리케이션 서비스는 유스케이스와 트랜잭션 경계를 조정하며, 저장소는 MySQL에 접근하고,
인프라 클라이언트는 커밋된 주문 데이터를 MockAPI.io의 `orders` 리소스와 조정합니다.

```text
클라이언트
  -> 컨트롤러
  -> 애플리케이션 서비스
  -> 저장소 / 데이터 수집 클라이언트
  -> MySQL / MockAPI.io
```

의존성은 HTTP·인프라 어댑터에서 애플리케이션 서비스와 도메인 모델의 안쪽으로 향합니다.
컨트롤러가 저장소에 직접 접근하지 않습니다.

## 2. 패키지 구조

```text
dev.nbcsparta.assignment.nbccoffeeordersystem
├── domain
│   ├── menu
│   ├── user
│   ├── point
│   ├── order
│   └── idempotency
├── infrastructure
│   └── collector
└── global
    ├── config
    ├── exception
    └── response
```

각 도메인 패키지는 필요한 컨트롤러, DTO, 엔티티, 저장소, 서비스를 포함할 수 있습니다. 패키지
구조는 유스케이스를 지원해야 하며, 인위적인 파사드나 저장소당 하나의 서비스 규칙을 강제하지
않습니다.

## 3. 애플리케이션 서비스

### `MenuService`

메뉴 카탈로그를 읽고 메뉴 응답 데이터를 반환합니다.

### `PointChargeService`

포인트 충전 트랜잭션을 담당합니다. 멱등성 기록을 검증하고 원자적 포인트 증가를 적용하며,
완료된 멱등성 응답을 같은 트랜잭션에 저장합니다.

### `OrderPaymentService`

주문 결제 트랜잭션을 담당합니다. 하나의 원자적 유스케이스에 참여하므로 사용자, 메뉴, 주문,
멱등성 저장소를 사용할 수 있습니다.

처리 순서는 다음과 같습니다.

1. 공유 멱등성 기록을 확보하거나 조회합니다.
2. 완료된 동일 요청이면 저장된 응답을 반환합니다.
3. 메뉴 가격을 조회합니다.
4. DB 조건부 감소로 포인트를 차감합니다.
5. 결제 시점의 금액으로 결제 주문을 하나 생성합니다.
6. 완료된 멱등성 응답을 저장합니다.
7. `collectionStatus = PENDING` 상태로 주문을 생성합니다.
8. 트랜잭션이 커밋된 뒤에만 주문 완료 이벤트를 발행합니다.

### `OrderCollectionDeliveryService`

완료 주문의 수집 전송 시도를 담당합니다. 대기 주문의 `orderId`, `userId`, `menuId`,
`paymentAmount`를 `DataCollectionClient`에 전달합니다. 각 시도는 새 DB 트랜잭션에서 아직
`PENDING`인 특정 주문만 DB 비관적 잠금으로 조회합니다. 이 잠금은 여러 애플리케이션 인스턴스의
정상적인 동시 시도를 직렬화하므로 같은 주문을 동시에 조정하지 않습니다.

`MockApiDataCollectionClient`는 먼저 `orderId`로 MockAPI.io의 `orders` 리소스를 조회합니다.
payload가 같은 레코드 한 건이 있으면 이미 완료된 전송으로 처리하고, 없으면 `POST /orders`를
호출합니다. 여러 레코드가 있거나 같은 `orderId`의 주문 데이터가 다르면 충돌입니다. 조회 또는
생성이 성공하면 주문을 `SUCCEEDED`로 표시합니다. 전송 오류, 타임아웃, 성공하지 않은 응답,
잘못된 응답 또는 데이터 충돌이 발생하면 `PENDING`으로 두어 스케줄러가 나중에 재시도합니다.

### `OrderCollectionRetryScheduler`

주기적으로 `PENDING` 주문을 찾아 `OrderCollectionDeliveryService`에 전달합니다. 전달 서비스는
각 대기 주문을 다시 확인하고 비관적 잠금을 획득하며, 모든 재시도에서 레코드를 생성하기 전에
`orderId`로 조회하므로 모든 애플리케이션 인스턴스에서 실행해도 안전합니다.

### `PopularMenuService`

현재 날짜를 제외한 `Asia/Seoul` 기준 직전 완료 7일의 Redis ZSET을 읽고 합산합니다. 주문 수
내림차순, 메뉴 ID 오름차순으로 정렬해 최대 3건을 반환합니다.

MySQL은 기준 데이터입니다. Redis를 사용할 수 없거나 일별 캐시 키가 없거나 유효하지 않으면
해당 날짜의 `coffee_order` 행을 집계해 ZSET projection을 재구축합니다.

### `PopularMenuCacheUpdater`

커밋된 주문 이벤트를 받아 주문 메뉴 멤버를 해당 일자의 Redis ZSET에 증가시킵니다. Redis
스크립트가 주문 ID의 `SETNX` projection marker 생성과 `ZINCRBY`를 한 번에 수행하므로,
재시도 이벤트가 같은 주문을 두 번 증가시킬 수 없습니다. DB 트랜잭션이 롤백된 주문에는 Redis
갱신을 수행하지 않습니다.

## 4. 동시성 및 멱등성

DB는 모든 애플리케이션 인스턴스의 요청을 조정합니다.

- 포인트 충전은 원자적 증가 쿼리를 사용합니다.
- 결제는 잔액이 가격 이상일 때만 성공하는 조건부 감소 쿼리를 사용합니다.
- `(operation, idempotency_key)` 유니크 제약은 중복 변경 요청을 직렬화합니다.
- 완료된 동일 멱등성 기록은 저장된 응답을 재생합니다.
- 같은 키를 다른 요청 해시에 사용하면 거부합니다.
- 커밋된 주문은 일별 Redis ZSET 멤버에 한 번만 projection됩니다.
- 수집 전송 시도는 외부 호출 전에 아직 `PENDING`인 주문 행을 비관적으로 잠가, 정상적인 여러
  인스턴스가 같은 주문을 동시에 전송하지 못하게 합니다.

멱등성은 하나의 요청이 반복되어 부작용이 중복되는 것을 막습니다. 원자적 포인트 갱신은 서로
다른 동시 요청으로 인한 갱신 유실과 초과 결제를 막습니다. 두 방식 모두 필요합니다.

Redis는 인기 메뉴 읽기 성능을 높이지만 MySQL 주문 데이터를 대체하지 않습니다. 캐시 복구는
MySQL 주문을 집계해 Redis projection을 복원합니다.

## 5. 데이터 수집

`OrderPaymentService`는 결제 트랜잭션 안에서 영속적인 전송 상태의 주문을 만들고, 커밋 후
이벤트를 발행합니다. 이벤트는 즉시 best-effort 전송을 유발하며, 호출에 실패하거나 프로세스가
중단되면 재시도 스케줄러가 복구합니다. `DataCollectionClient`는 애플리케이션 서비스를 HTTP와
분리하고, `MockApiDataCollectionClient`가 실제 MockAPI.io 계약을 구현합니다. 테스트에서는 이
포트를 대체하거나 로컬 HTTP 스텁으로 어댑터를 검증하며 실제 MockAPI.io 프로젝트에 의존하지
않습니다.

```text
주문 결제 트랜잭션 커밋
  -> OrderCompletedEvent
  -> OrderCollectionDeliveryService가 PENDING 주문 잠금
  -> DataCollectionClient가 (orderId, userId, menuId, paymentAmount)를 받음
  -> GET MockAPI.io /orders?orderId={orderId}
       -> 동일 레코드 한 건: 이미 완료된 전송
       -> 레코드 없음: POST MockAPI.io /orders
       -> 충돌 또는 호출 실패: PENDING 유지
  -> 조회 또는 생성 성공 후 SUCCEEDED로 표시

스케줄러 재시도
  -> PENDING 주문 선택
  -> 아직 PENDING인 주문 잠금
  -> MockAPI.io 생성 전 조회 흐름 반복
  -> SUCCEEDED로 표시하거나 PENDING 유지
```

외부 호출 실패는 커밋된 결제를 되돌리지 않습니다. 주문 행은 이후 재시도를 위해 대기 상태를
유지합니다. MockAPI.io가 생성을 수락했지만 응답이나 로컬 `SUCCEEDED` 커밋이 유실되면 다음
시도에서 새 레코드를 만들기 전에 기존 `orderId`를 찾습니다. 비관적 잠금과 생성 전 조회는
애플리케이션이 소유한 전송 시도를 보호하지만 MockAPI.io는 로컬 트랜잭션과 연결된 유니크 제약을
제공하지 않습니다. 같은 외부 리소스에 대한 수동 또는 무관한 쓰기는 애플리케이션의 일관성 경계
밖에 있습니다.

MockAPI.io 프로젝트 Base URL은 외부 설정이며 저장소에 커밋하지 않습니다. 어댑터는 고정된
`/orders` 리소스 경로를 덧붙이고 `Content-Type: application/json`으로 JSON을 전송하며, 설정된
연결·읽기 타임아웃을 사용합니다. HTTP 클라이언트는 `POST`를 자동 재시도하지 않아야 하며,
복구는 영속적인 `PENDING` 상태와 공유 스케줄러가 담당합니다.
