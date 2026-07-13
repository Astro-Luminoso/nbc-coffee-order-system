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
```

```json
{
  "amount": 10000
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
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

### Errors

| Condition | Status | Code |
|-----------|--------|------|
| Invalid `userId` or non-positive/missing `amount` | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |

## 5. Order and Pay for Coffee

Creates an order for one menu item with quantity `1` and pays for it entirely with the user's points.
Point deduction, order creation, and order-item creation form one atomic database operation.

### Request

```http
POST /api/v1/orders
Content-Type: application/json
Accept: application/json
```

```json
{
  "userId": 1,
  "menuId": 2
}
```

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
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

The `paymentAmount` is captured from the menu price at payment time. After the database transaction commits, the application sends the following payload to the configured data collection platform in near real time:

```json
{
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

A data collection delivery failure does not reverse an already committed payment or order. The application must expose the failure through its operational logging and apply the delivery-failure handling selected by the technical design.

### Errors

| Condition | Status | Code |
|-----------|--------|------|
| Missing or invalid request field | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |
| Menu does not exist | `404 Not Found` | `MENU_NOT_FOUND` |
| User balance is lower than the current menu price | `409 Conflict` | `INSUFFICIENT_POINTS` |

For every failed request, no points are deducted and no order or order item is created.

## 6. List Popular Menus

Returns up to three menus ranked by successfully paid order count across the current calendar date and the previous six dates in the configured business timezone.

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

The period is inclusive at both ends. Only paid orders whose payment completion date falls within the returned period are counted.
If no paid orders exist in the period, the endpoint returns `200 OK` with an empty `menus` array.

## 7. Consistency Guarantees

- A successful point charge is reflected exactly once in the returned balance for that request.
- A successful order response means the point deduction, order, and order item have committed together.
- A rejected order leaves the user's balance and order data unchanged.
- Every order created by this API contains one order item with quantity `1`.
- Popularity includes only successfully paid orders and counts each successful order once.
- MySQL order data remains the source of truth when the Redis popularity projection requires reconciliation.
