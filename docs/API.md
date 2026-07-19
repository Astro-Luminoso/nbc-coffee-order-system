# Coffee Order System API Specification

## 1. Overview

All endpoints use the `/api/v1` base path and return a
`CommonApiResponse<T>` envelope. Shared validation, response, error, and
idempotency rules are defined in [`CONVENTION.md`](CONVENTION.md).

## 2. Public Endpoint Summary

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

After commit, the application passes this payload to the configured data
collection client:

```json
{
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

The order is created with a pending collection-delivery state in the same
transaction as payment. The application attempts delivery after commit. An
external delivery failure does not undo a committed payment; it records a
pending state on the order and is retried by a shared scheduler.

## 6. MockAPI.io Order Collection (Outbound)

This is an outbound integration contract, not a public coffee-order-system
endpoint. The MockAPI.io project base URL is supplied through external
configuration and already includes the project API prefix. The resource path is
fixed to `/orders`.

### 6.1 Resource Schema

| Field | Direction | Type | Constraints |
|---|---|---|---|
| `id` | Response only | String | MockAPI.io-generated resource identifier; not stored locally |
| `orderId` | Request and response | Number | Positive local paid-order identifier and reconciliation key |
| `userId` | Request and response | Number | Positive local user identifier |
| `menuId` | Request and response | Number | Positive local menu identifier |
| `paymentAmount` | Request and response | Number | Positive integer points captured at payment time |

### 6.2 Lookup Before Create

Every initial attempt and retry first filters the external collection by the
local order ID.

```http
GET ${MOCKAPI_BASE_URL}/orders?orderId=1001
Accept: application/json
```

A previously delivered order returns a collection such as:

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

Exactly one record whose order fields match the local order completes the
delivery without another create request. An empty array proceeds to the create
request. Multiple records, or a matching `orderId` with different order fields,
are external-data conflicts and leave the local order `PENDING`.

### 6.3 Create Collected Order

When lookup returns an empty array, the client creates the resource.

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

A successful MockAPI.io response contains the created record and its generated
`id`:

```json
{
  "id": "42",
  "orderId": 1001,
  "userId": 1,
  "menuId": 2,
  "paymentAmount": 5000
}
```

The client accepts a `2xx` response only when its JSON body can be parsed and
the returned order fields match the request. It does not persist the MockAPI.io
`id`; the local `orderId` remains the reconciliation key.

Transport errors, timeouts, non-`2xx` responses, malformed bodies, and data
conflicts are failed delivery attempts. They do not alter the committed payment
and leave `collection_status = PENDING` for the shared scheduler. The HTTP
client must not automatically retry `POST`, because the next scheduled attempt
must perform lookup before create.

## 7. List Popular Menus

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

## 8. Errors

| Condition | Status | Code |
|---|---|---|
| Invalid path, header, or request body | `400 Bad Request` | `INVALID_REQUEST` |
| User does not exist | `404 Not Found` | `USER_NOT_FOUND` |
| Menu does not exist | `404 Not Found` | `MENU_NOT_FOUND` |
| User has insufficient points | `409 Conflict` | `INSUFFICIENT_POINTS` |
| Same idempotency key with a different request | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| Unexpected failure | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 9. Consistency Guarantees

- A successful point charge is applied exactly once for its operation and key.
- A successful order means the point deduction and order creation committed
  together.
- A matching repeated order request returns its original response without
  creating another order.
- A failed payment leaves the balance and order data unchanged.
- A successful payment creates one order with a durable pending collection
  delivery state. Delivery may be attempted more than once, but every attempt
  queries MockAPI.io by `orderId` before it creates a record.
- One identical MockAPI.io record completes delivery. No record is created when
  lookup finds an identical delivery, and conflicting external data remains a
  failed pending delivery.
- Popular-menu counts are cached in Redis and can be rebuilt from committed
  MySQL order data without changing the returned count.
