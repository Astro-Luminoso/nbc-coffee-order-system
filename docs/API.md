# Coffee Order System API Specification

## 1. Overview

All endpoints use the `/api/v1` base path and return a
`CommonApiResponse<T>` envelope. Shared validation, response, error, and
idempotency rules are defined in [`CONVENTION.md`](CONVENTION.md).

## 2. Endpoint Summary

| Feature | Method | Path | Success status |
|---|---|---|---|
| List coffee menus | `GET` | `/api/v1/menus` | `200 OK` |
| Charge points | `POST` | `/api/v1/users/{userId}/point-charges` | `200 OK` |
| Order and pay | `POST` | `/api/v1/orders` | `201 Created` |
| List popular menus | `GET` | `/api/v1/menus/popular` | `200 OK` |

## 3. List Coffee Menus

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

The result is ordered by menu ID ascending. An empty catalog returns an empty
`menus` array.

## 4. Charge Points

```http
POST /api/v1/users/1/point-charges
Content-Type: application/json
Idempotency-Key: 9a61f4aa-a8c7-40ef-bffd-31c82a39ed34

{
  "amount": 10000
}
```

| Field | Location | Constraints |
|---|---|---|
| `userId` | Path | Positive 64-bit integer |
| `Idempotency-Key` | Header | Non-blank ASCII string, 1 to 128 characters |
| `amount` | Body | Positive integer; one won equals one point |

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

The same key and request return this original response without another charge.

## 5. Order and Pay for Coffee

```http
POST /api/v1/orders
Content-Type: application/json
Idempotency-Key: 1f2959a8-c600-49f8-91e7-bb7a0d8ea70c

{
  "userId": 1,
  "menuId": 2
}
```

| Field | Location | Constraints |
|---|---|---|
| `Idempotency-Key` | Header | Non-blank ASCII string, 1 to 128 characters |
| `userId` | Body | Positive 64-bit integer |
| `menuId` | Body | Positive 64-bit integer |

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

The application deducts points and creates the order in one database
transaction. The payment amount is the menu price at payment time. Repeating a
completed matching request returns the original `201 Created` response without
another deduction or order.

After commit, the application sends this payload to the configured data
collection client:

```json
{
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

An external delivery failure does not undo a committed payment.

## 6. List Popular Menus

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

The period contains the seven completed `Asia/Seoul` calendar dates immediately
before the current date. The current date is excluded. Results include only
committed paid orders, are ordered by `orderCount` descending and `menuId`
ascending, and contain at most three menus.

Popularity counts are read from Redis daily ZSETs. MySQL paid orders remain the
source of truth: a missing, unavailable, or invalid Redis cache is rebuilt from
MySQL aggregation before the response is returned.

## 7. Errors

| Condition | Status | Code |
|---|---|---|
| Invalid path, header, or request body | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |
| Menu does not exist | `404 Not Found` | `MENU_NOT_FOUND` |
| User has insufficient points | `409 Conflict` | `INSUFFICIENT_POINTS` |
| Same idempotency key with a different request | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| Unexpected failure | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 8. Consistency Guarantees

- A successful point charge is applied exactly once for its operation and key.
- A successful order means the point deduction and order creation committed
  together.
- A matching repeated order request returns its original response without
  creating another order.
- A failed payment leaves the balance and order data unchanged.
- Popular-menu counts are cached in Redis and can be rebuilt from committed
  MySQL order data without changing the returned count.
