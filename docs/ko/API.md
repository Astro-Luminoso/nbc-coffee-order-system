# 커피 주문 시스템 API 명세서

> 원문: [English API Specification](../API.md)

## 1. 개요

모든 엔드포인트는 `/api/v1` 기본 경로를 사용하고 `CommonApiResponse<T>` 형식으로
응답합니다. 공통 검증, 응답, 오류, 멱등성 규칙은
[컨벤션](CONVENTION.md)에 정의되어 있습니다.

## 2. 엔드포인트 요약

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

커밋 뒤 애플리케이션은 설정된 데이터 수집 클라이언트로 다음 데이터를 전송합니다.

```json
{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

`orderId`는 외부 전송의 멱등성 식별자입니다. 수집 플랫폼은 같은 주문 ID의 중복 payload를
무시해야 합니다.

주문은 결제와 같은 트랜잭션에서 `PENDING` 수집 전송 상태로 생성됩니다. 애플리케이션은
커밋 후 전송을 시도합니다. 외부 전송 실패는 커밋된 결제를 되돌리지 않으며, 주문은 대기
상태로 남고 공유 스케줄러가 나중에 재시도합니다.

## 6. 인기 메뉴 목록 조회

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

## 7. 오류

| 상황 | 상태 | 코드 |
|---|---|---|
| 잘못된 경로, 헤더 또는 요청 본문 | `400 Bad Request` | `INVALID_REQUEST` |
| 사용자가 없음 | `404 Not Found` | `USER_NOT_FOUND` |
| 메뉴가 없음 | `404 Not Found` | `MENU_NOT_FOUND` |
| 포인트 부족 | `409 Conflict` | `INSUFFICIENT_POINTS` |
| 같은 멱등키를 다른 요청에 사용 | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| 예기치 않은 실패 | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 8. 일관성 보장

- 성공한 포인트 충전은 작업 종류와 멱등키당 한 번만 적용됩니다.
- 성공한 주문은 포인트 차감과 주문 생성이 함께 커밋됐음을 의미합니다.
- 같은 주문 요청을 반복하면 다른 주문을 만들지 않고 최초 응답을 반환합니다.
- 결제 실패 시 잔액과 주문 데이터는 변경되지 않습니다.
- 성공한 결제는 영속적인 대기 전송 상태의 주문 하나를 만듭니다. 전송은 여러 번 시도될 수
  있으며 수신자는 `orderId`로 중복을 제거합니다.
- 인기 메뉴 수는 Redis에 캐시되며, 커밋된 MySQL 주문 데이터로 재구축해도 반환 수가
  달라지지 않습니다.
