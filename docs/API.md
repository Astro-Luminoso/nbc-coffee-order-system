# Coffee Order System API Specification

## 1. Overview

This document defines the HTTP API contract for the coffee order system described in [`PRD.md`](PRD.md).
Shared HTTP, response DTO, exception handling, and logging rules are defined in [`CONVENTION.md`](CONVENTION.md).

All response bodies use the common `CommonApiResponse<T>` envelope.

## 2. Endpoint Summary

| Feature                      | Method | Path                                              | Success status |
|------------------------------|--------|---------------------------------------------------|----------------|
| List coffee menus            | `GET`  | `/api/v1/menus`                                   | `200 OK`       |
| Charge points                | `POST` | `/api/v1/users/{userId}/point-charges`            | `200 OK`       |
| Create an order attempt      | `POST` | `/api/v1/order-attempts`                          | `201 Created`  |
| Confirm and pay for an order | `POST` | `/api/v1/order-attempts/{orderAttemptId}/confirm` | `201 Created`  |
| List popular menus           | `GET`  | `/api/v1/menus/popular`                           | `200 OK`       |

## 3. List Coffee Menus

Returns every available coffee menu. Results are ordered by `id` in ascending order so that repeated requests are deterministic.

### Request

```http
GET /api/v1/menus
Accept: application/json
```

### Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "httpStatus": 200,
  "data": {
    "menus": [
      {
        "id": 1,
        "name": "Americano",
        "price": 4500
      },
      {
        "id": 2,
        "name": "Cafe Latte",
        "price": 5000
      }
    ]
  }
}
```

An empty catalog returns `200 OK` with an empty `menus` array.

## 4. Charge Points

Adds a positive amount to the identified user's point balance. Each won supplied as `amount` is converted to one point.
Concurrent successful charges must not lose balance updates.

### Request

```http
POST /api/v1/users/1/point-charges
Content-Type: application/json
Accept: application/json
Idempotency-Key: 9a61f4aa-a8c7-40ef-bffd-31c82a39ed34
```

```json
{
  "amount": 10000
}
```

| Field             | Type    | Required | Constraints                            | Description                                |
|-------------------|---------|----------|----------------------------------------|--------------------------------------------|
| `Idempotency-Key` | string  | Yes      | Valid value defined in `CONVENTION.md` | Header that identifies this charge attempt |
| `userId`          | integer | Yes      | Positive 64-bit integer                | Path parameter identifying the user        |
| `amount`          | integer | Yes      | Greater than `0`                       | Amount in won to convert to points         |

### Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

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

The returned `balance` is the balance immediately after this charge is applied.
Repeating the same request with the same `Idempotency-Key` returns the original `200 OK` status and response body without applying another charge.

### Errors

| Condition                                                                                | Status            | Code                     |
|------------------------------------------------------------------------------------------|-------------------|--------------------------|
| Missing or invalid `Idempotency-Key`, invalid `userId`, or non-positive/missing `amount` | `400 Bad Request` | `INVALID_REQUEST`        |
| User does not exist                                                                      | `404 Not Found`   | `USER_NOT_FOUND`         |
| The same `Idempotency-Key` is reused with a different request                            | `409 Conflict`    | `IDEMPOTENCY_KEY_REUSED` |

## 5. Create an Order Attempt

Creates a durable order attempt before the client submits the final payment confirmation. The server generates the attempt identifier, stores the immutable order request, and keeps the attempt until it is completed or expires. A client may persist or synchronize this identifier across browser sessions without generating its own idempotency key.

Repeating attempt creation may create another `PENDING` attempt because this endpoint has no client idempotency key. No payment occurs until confirmation, so the client must retain and confirm only one returned `orderAttemptId`; abandoned attempts expire automatically.

### Request

```http
POST /api/v1/order-attempts
Content-Type: application/json
Accept: application/json
```

```json
{
  "userId": 1,
  "menuId": 2
}
```

| Field    | Type    | Required | Constraints             | Description                               |
|----------|---------|----------|-------------------------|-------------------------------------------|
| `userId` | integer | Yes      | Positive 64-bit integer | User who will place and pay for the order |
| `menuId` | integer | Yes      | Positive 64-bit integer | Menu selected for purchase                |

### Response

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "httpStatus": 201,
  "data": {
    "orderAttemptId": "d414f36e-c9e5-407b-bf01-8759f6a590c3",
    "status": "PENDING",
    "expiresAt": "2026-07-14T15:00:00+09:00"
  }
}
```

The response means only that a `PENDING` attempt was stored. No points are deducted and no `coffee_order`, `order_item`, or `order_outbox` row is created at this stage.

### Errors

| Condition                        | Status            | Code              |
|----------------------------------|-------------------|-------------------|
| Missing or invalid request field | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist              | `404 Not Found`   | `USER_NOT_FOUND`  |
| Menu does not exist              | `404 Not Found`   | `MENU_NOT_FOUND`  |

## 6. Confirm Order and Pay for Coffee

Confirms one server-issued order attempt, creates an order for one menu item with quantity `1`, and pays for it entirely with the user's points. The confirmation request contains no mutable order data; the server uses the request stored with the attempt.

### Request

```http
POST /api/v1/order-attempts/d414f36e-c9e5-407b-bf01-8759f6a590c3/confirm
Accept: application/json
```

| Field            | Type   | Required | Constraints                                                   | Description                                  |
|------------------|--------|----------|---------------------------------------------------------------|----------------------------------------------|
| `orderAttemptId` | string | Yes      | Server-issued non-blank ASCII value of at most 128 characters | Path parameter identifying the order attempt |

### Response

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

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
    "quantity": 1,
    "paymentAmount": 5000,
    "remainingBalance": 10000,
    "orderedAt": "2026-07-13T14:30:00+09:00"
  }
}
```

The endpoint returns `201 Created` only after one database transaction has committed the point deduction, order, order item, durable delivery task, and completed idempotency result. The `paymentAmount` is captured from the current menu price at confirmation time.

The server serializes concurrent confirmations by locking the attempt record. Repeating a confirmation for a `COMPLETED` attempt returns the stored original `201 Created` status and response body without deducting points, creating another order, or creating another delivery task.

If validation, a business rule, or the database transaction fails before commit, the attempt remains `PENDING`; the client may confirm that same attempt again. A connection loss after a successful commit does not make the transaction a failed order because the next confirmation replays the completed result.

After commit, a worker reads the immutable `coffee_order` and `order_item` rows referenced by `order_outbox.order_id` and constructs this data-collection payload:

```json
{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

Delivery is at least once. The worker retries the durable task until the data collection platform acknowledges it, which provides eventual delivery when the platform eventually becomes available. The platform uses `orderId` as its idempotency key to ignore duplicate deliveries. A delivery failure after commit does not reverse the payment or order and does not change the original `201 Created` response. The application records the failure in Korean operational logs and continues retrying.

### Errors

| Condition                                                     | Status                      | Code                      |
|---------------------------------------------------------------|-----------------------------|---------------------------|
| Missing or invalid `orderAttemptId`                           | `400 Bad Request`           | `INVALID_REQUEST`         |
| Order attempt does not exist                                  | `404 Not Found`             | `ORDER_ATTEMPT_NOT_FOUND` |
| User does not exist                                           | `404 Not Found`             | `USER_NOT_FOUND`          |
| Menu does not exist                                           | `404 Not Found`             | `MENU_NOT_FOUND`          |
| User balance is lower than the current menu price             | `409 Conflict`              | `INSUFFICIENT_POINTS`     |
| Order attempt has expired                                     | `410 Gone`                  | `ORDER_ATTEMPT_EXPIRED`   |
| The order transaction fails or its commit cannot be confirmed | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR`   |

Validation and business failures leave the user's points and order data unchanged. If the database transaction rolls back, no point deduction, order, order item, completed idempotency result, or delivery task is persisted.

## 7. List Popular Menus

Returns up to three menus ranked by successfully paid order count across the seven completed calendar dates immediately before the current date in the `Asia/Seoul` business timezone. The current date is excluded.

Results are ordered by:

1. `orderCount` descending.
2. `menuId` ascending when order counts are equal.

### Request

```http
GET /api/v1/menus/popular
Accept: application/json
```

### Response

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "httpStatus": 200,
  "data": {
    "period": {
      "startDate": "2026-07-07",
      "endDate": "2026-07-13"
    },
    "menus": [
      {
        "menuId": 2,
        "name": "Cafe Latte",
        "price": 5000,
        "orderCount": 42
      },
      {
        "menuId": 1,
        "name": "Americano",
        "price": 4500,
        "orderCount": 38
      }
    ]
  }
}
```

The period is inclusive at both ends. For example, when the current date is `2026-07-14`, the period is `2026-07-07` through `2026-07-13`. Only paid orders whose payment completion date falls within the returned period are counted.
If no paid orders exist in the period, the endpoint returns `200 OK` with an empty `menus` array.

## 8. Consistency Guarantees

- A successful point charge is reflected exactly once in the returned balance for that request.
- A successful order response means the point deduction, order, and order item have committed together.
- A repeated point charge with the same `Idempotency-Key` and request returns its original response without repeating side effects.
- Reusing a point-charge `Idempotency-Key` with a different request is rejected.
- A repeated confirmation of the same completed `orderAttemptId` returns its original response without repeating side effects.
- A rejected order leaves the user's balance and order data unchanged.
- Every order created by this API contains one order item with quantity `1`.
- Popularity includes only successfully paid orders and counts each successful order once. The popular-menu response derives its counts from MySQL paid-order data; Redis is a rebuildable cache and does not determine the result.
- Every committed order has one durable delivery task, and delivery retries use `orderId` to prevent duplicate processing by the data collection platform.
- MySQL order data remains the source of truth when the Redis popularity cache requires reconstruction. The application rebuilds a lost or inconsistent daily ZSET from MySQL without changing the popular-menu response.
