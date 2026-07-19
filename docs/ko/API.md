# 커피 주문 시스템 API 명세서

> 원문: [English API Specification](../API.md)

## 1. 개요

모든 엔드포인트는 `/api/v1` 기본 경로를 사용하고 `CommonApiResponse<T>` 형식으로
응답합니다. 공통 검증, 응답, 오류, 멱등성 규칙은
[컨벤션](CONVENTION.md)에 정의되어 있습니다.

## 2. 공개 엔드포인트 요약

| 기능 | 메서드 | 경로 | 성공 상태 |
|---|---|---|---|
| 커피 메뉴 목록 조회 | `GET` | `/api/v1/menus` | `200 OK` |
| 포인트 충전 | `POST` | `/api/v1/users/{userId}/point-charges` | `200 OK` |
| 주문·결제 | `POST` | `/api/v1/orders` | `201 Created` |
| 인기 메뉴 목록 조회 | `GET` | `/api/v1/menus/popular` | `200 OK` |

## 3. 커피 메뉴 목록 조회

```http
GET /api/v1/menus
Accept: application/json
```

```json
{
  "httpStatus": 200,
  "data": {
    "menus": [
      { "id": 1, "name": "Americano", "price": 4500 },
      { "id": 2, "name": "Cafe Latte", "price": 5000 }
    ]
  }
}
```

결과는 메뉴 ID 오름차순으로 정렬됩니다. 메뉴가 없으면 빈 `menus` 배열을 반환합니다.

## 4. 포인트 충전

```http
POST /api/v1/users/1/point-charges
Content-Type: application/json
Idempotency-Key: 9a61f4aa-a8c7-40ef-bffd-31c82a39ed34

{
  "amount": 10000
}
```

| 필드 | 위치 | 제약 조건 |
|---|---|---|
| `userId` | 경로 | 양의 64비트 정수 |
| `Idempotency-Key` | 헤더 | 공백이 아닌 1~128자의 ASCII 문자열 |
| `amount` | 본문 | 양의 정수, 1원은 1포인트 |

```json
{
  "httpStatus": 200,
  "data": {
    "userId": 1,
    "chargedAmount": 10000,
    "balance": 15000
  }
}
```

같은 키와 같은 요청을 다시 보내면 포인트를 다시 충전하지 않고 최초 응답을 반환합니다.

## 5. 커피 주문·결제

```http
POST /api/v1/orders
Content-Type: application/json
Idempotency-Key: 1f2959a8-c600-49f8-91e7-bb7a0d8ea70c

{
  "userId": 1,
  "menuId": 2
}
```

| 필드 | 위치 | 제약 조건 |
|---|---|---|
| `Idempotency-Key` | 헤더 | 공백이 아닌 1~128자의 ASCII 문자열 |
| `userId` | 본문 | 양의 64비트 정수 |
| `menuId` | 본문 | 양의 64비트 정수 |

```json
{
  "httpStatus": 201,
  "data": {
    "orderId": 1001,
    "userId": 1,
    "menu": {
      "id": 2,
      "name": "Cafe Latte"
    },
    "paymentAmount": 5000,
    "remainingBalance": 10000,
    "orderedAt": "2026-07-19T14:30:00+09:00"
  }
}
```

애플리케이션은 하나의 DB 트랜잭션 안에서 포인트를 차감하고 주문을 생성합니다. 결제 금액은
결제 시점의 메뉴 가격입니다. 같은 요청을 완료된 멱등키로 재시도하면 포인트 차감이나 주문
생성 없이 최초의 `201 Created` 응답을 반환합니다.

커밋 뒤 애플리케이션은 설정된 데이터 수집 클라이언트로 다음 데이터를 전달합니다.

```json
{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

주문은 결제와 같은 트랜잭션에서 `PENDING` 수집 전송 상태로 생성됩니다. 애플리케이션은
커밋 후 전송을 시도합니다. 외부 전송 실패는 커밋된 결제를 되돌리지 않으며, 주문은 대기
상태로 남고 공유 스케줄러가 나중에 재시도합니다.

## 6. MockAPI.io 주문 수집(Outbound)

이 계약은 외부 연동용이며 커피 주문 시스템의 공개 엔드포인트가 아닙니다. MockAPI.io 프로젝트
Base URL은 외부 설정으로 제공하며 프로젝트 API prefix까지 포함합니다. 리소스 경로는
`/orders`로 고정합니다.

### 6.1 리소스 스키마

| 필드 | 방향 | 타입 | 제약 조건 |
|---|---|---|---|
| `id` | 응답 전용 | 문자열 | MockAPI.io가 생성하는 리소스 식별자이며 로컬에 저장하지 않음 |
| `orderId` | 요청·응답 | 숫자 | 양의 로컬 결제 주문 식별자이자 조정 키 |
| `userId` | 요청·응답 | 숫자 | 양의 로컬 사용자 식별자 |
| `menuId` | 요청·응답 | 숫자 | 양의 로컬 메뉴 식별자 |
| `paymentAmount` | 요청·응답 | 숫자 | 결제 시점에 확정된 양의 정수 포인트 |

### 6.2 생성 전 조회

최초 전송과 모든 재시도는 먼저 로컬 주문 ID로 외부 수집 데이터를 필터링합니다.

```http
GET ${MOCKAPI_BASE_URL}/orders?orderId=1001
Accept: application/json
```

이미 전송된 주문은 다음과 같은 배열로 응답합니다.

```json
[
  {
    "id": "42",
    "orderId": 1001,
    "userId": 1,
    "menuId": 2,
    "paymentAmount": 5000
  }
]
```

로컬 주문 필드가 모두 같은 레코드가 정확히 한 건이면 생성 요청 없이 전송을 완료합니다. 빈
배열이면 생성 요청을 수행합니다. 레코드가 여러 건이거나 같은 `orderId`의 주문 필드가 다르면
외부 데이터 충돌로 처리하고 로컬 주문을 `PENDING` 상태로 유지합니다.

### 6.3 수집 주문 생성

조회 결과가 빈 배열이면 클라이언트가 리소스를 생성합니다.

```http
POST ${MOCKAPI_BASE_URL}/orders
Content-Type: application/json
Accept: application/json

{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

MockAPI.io가 성공하면 생성한 레코드와 자체 `id`를 반환합니다.

```json
{
  "id": "42",
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

클라이언트는 `2xx` 상태이고 JSON 본문을 파싱할 수 있으며 반환된 주문 필드가 요청과 일치할
때만 성공으로 처리합니다. MockAPI.io의 `id`는 저장하지 않고 로컬 `orderId`를 조정 키로
사용합니다.

전송 오류, 타임아웃, `2xx`가 아닌 응답, 잘못된 본문, 데이터 충돌은 전송 실패입니다. 커밋된
결제는 변경하지 않고 공유 스케줄러의 재시도를 위해 `collection_status = PENDING`을 유지합니다.
다음 스케줄 전송이 생성 전에 조회해야 하므로 HTTP 클라이언트는 `POST`를 자동 재시도하지
않습니다.

## 7. 인기 메뉴 목록 조회

```http
GET /api/v1/menus/popular
Accept: application/json
```

```json
{
  "httpStatus": 200,
  "data": {
    "periodStartDate": "2026-07-12",
    "periodEndDate": "2026-07-18",
    "menus": [
      {
        "menuId": 2,
        "name": "Cafe Latte",
        "price": 5000,
        "orderCount": 42
      }
    ]
  }
}
```

조회 기간은 현재 날짜를 제외한 `Asia/Seoul` 기준 직전 완료 7일입니다. 결과에는 커밋된
결제 주문만 포함되며, `orderCount` 내림차순과 `menuId` 오름차순으로 정렬해 최대 3건을
반환합니다.

인기 메뉴 수는 Redis 날짜별 ZSET에서 읽습니다. MySQL 주문 데이터가 기준 데이터이며,
Redis 캐시 키가 없거나 사용할 수 없거나 유효하지 않으면 MySQL 집계로 캐시를 재구축한 뒤
응답합니다.

## 8. 오류

| 상황 | 상태 | 코드 |
|---|---|---|
| 잘못된 경로, 헤더 또는 요청 본문 | `400 Bad Request` | `INVALID_REQUEST` |
| 사용자가 없음 | `404 Not Found` | `USER_NOT_FOUND` |
| 메뉴가 없음 | `404 Not Found` | `MENU_NOT_FOUND` |
| 포인트 부족 | `409 Conflict` | `INSUFFICIENT_POINTS` |
| 같은 멱등키를 다른 요청에 사용 | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| 예기치 않은 실패 | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 9. 일관성 보장

- 성공한 포인트 충전은 작업 종류와 멱등키당 한 번만 적용됩니다.
- 성공한 주문은 포인트 차감과 주문 생성이 함께 커밋됐음을 의미합니다.
- 같은 주문 요청을 반복하면 다른 주문을 만들지 않고 최초 응답을 반환합니다.
- 결제 실패 시 잔액과 주문 데이터는 변경되지 않습니다.
- 성공한 결제는 영속적인 대기 전송 상태의 주문 하나를 만듭니다. 전송은 여러 번 시도될 수
  있지만 모든 시도는 레코드를 생성하기 전에 `orderId`로 MockAPI.io를 조회합니다.
- 동일한 MockAPI.io 레코드 한 건이 있으면 전송을 완료합니다. 기존 전송이 조회되면 새 레코드를
  생성하지 않고, 외부 데이터가 충돌하면 대기 전송 실패로 유지합니다.
- 인기 메뉴 수는 Redis에 캐시되며, 커밋된 MySQL 주문 데이터로 재구축해도 반환 수가
  달라지지 않습니다.
