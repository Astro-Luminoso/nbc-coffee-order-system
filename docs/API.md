# Coffee Order System API Specification

## 1. Overview

This document defines the HTTP API contract for the coffee order system described in [`PRD.md`](PRD.md).
Shared HTTP, response DTO, exception handling, and logging rules are defined in [`CONVENTION.md`](CONVENTION.md).

All response bodies use the common `CommonApiResponse<T>` envelope.

## 2. Endpoint Summary

| Feature | Method | Path | Success status |
|---------|--------|------|----------------|
| List coffee menus | `GET` | `/api/v1/menus` | `200 OK` |
| Charge points | `POST` | `/api/v1/users/{userId}/point-charges` | `200 OK` |
| Order and pay for coffee | `POST` | `/api/v1/orders` | `201 Created` |
| List popular menus | `GET` | `/api/v1/menus/popular` | `200 OK` |

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

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `Idempotency-Key` | string | Yes | Valid value defined in `CONVENTION.md` | Header that identifies this charge attempt |
| `userId` | integer | Yes | Positive 64-bit integer | Path parameter identifying the user |
| `amount` | integer | Yes | Greater than `0` | Amount in won to convert to points |

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

| Condition | Status | Code |
|-----------|--------|------|
| Missing or invalid `Idempotency-Key`, invalid `userId`, or non-positive/missing `amount` | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |
| The same `Idempotency-Key` is reused with a different request | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |

## 5. Order and Pay for Coffee

Creates an order for one menu item with quantity `1` and pays for it entirely with the user's points.
Point deduction, order creation, and order-item creation form one atomic database operation.

### Request

```http
POST /api/v1/orders
Content-Type: application/json
Accept: application/json
Idempotency-Key: d414f36e-c9e5-407b-bf01-8759f6a590c3
```

```json
{
  "userId": 1,
  "menuId": 2
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `Idempotency-Key` | string | Yes | Valid value defined in `CONVENTION.md` | Header that identifies this order attempt |
| `userId` | integer | Yes | Positive 64-bit integer | User placing and paying for the order |
| `menuId` | integer | Yes | Positive 64-bit integer | Menu to purchase |

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

The endpoint returns `201 Created` only after the database transaction containing the point deduction, order, order item, idempotency result, and delivery event has committed successfully. The `paymentAmount` is captured from the menu price at payment time.

Repeating the same request with the same `Idempotency-Key` returns the original `201 Created` status and response body without deducting points, creating another order, or creating another delivery event.

After the transaction commits, the application sends the following payload to the configured data collection platform in near real time:

```json
{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

The application delivers this event at least once and retries from a durable delivery record until the data collection platform acknowledges it. The data collection platform uses `orderId` as the idempotency key and must ignore a duplicate event that has already been processed.

A data collection delivery failure does not reverse an already committed payment or order and does not change the original `201 Created` response. The application records the failure in Korean operational logs and continues retrying the delivery.

### Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing or invalid `Idempotency-Key` or request field | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |
| Menu does not exist | `404 Not Found` | `MENU_NOT_FOUND` |
| User balance is lower than the current menu price | `409 Conflict` | `INSUFFICIENT_POINTS` |
| The same `Idempotency-Key` is reused with a different request | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| The order transaction fails or its commit cannot be confirmed | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

Validation and business failures that occur before commit leave the user's points and order data unchanged. If the database transaction rolls back, no point deduction, order, order item, idempotency result, or delivery event is persisted.

A connection loss after a successful commit does not make the transaction a failed order. The client may safely repeat the request with the same `Idempotency-Key` to retrieve the original committed response without creating duplicate side effects.

## 6. List Popular Menus

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

## 7. Consistency Guarantees

- A successful point charge is reflected exactly once in the returned balance for that request.
- A successful order response means the point deduction, order, and order item have committed together.
- A repeated mutation with the same `Idempotency-Key` and request returns its original response without repeating side effects.
- Reusing an `Idempotency-Key` with a different request is rejected.
- A rejected order leaves the user's balance and order data unchanged.
- Every order created by this API contains one order item with quantity `1`.
- Popularity includes only successfully paid orders and counts each successful order once.
- Every committed order has one durable delivery event, and delivery retries use `orderId` to prevent duplicate processing by the data collection platform.
- MySQL order data remains the source of truth when the Redis popularity projection requires reconciliation.
