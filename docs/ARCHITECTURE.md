# API Architecture

## 1. Overview

The application uses a simple layered architecture. Controllers handle HTTP
concerns, application services coordinate use cases and transaction boundaries,
repositories access MySQL, and an infrastructure client sends committed order
data to the external collection platform.

```text
Client
  -> Controller
  -> Application Service
  -> Repository / Data Collection Client
  -> MySQL / External Platform
```

Dependencies flow inward from HTTP and infrastructure adapters to application
services and domain models. Controllers do not access repositories directly.

## 2. Package Structure

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

Each domain package may contain its controller, DTOs, entity, repository, and
service when those types are needed. Package structure must support the use
case; it must not force artificial facades or one-repository service classes.

## 3. Application Services

### `MenuService`

Reads the menu catalog and returns menu-facing response data.

### `PointChargeService`

Owns the point-charge transaction. It verifies the idempotency record, applies
an atomic point increment, and stores the completed idempotency response in the
same transaction.

### `OrderPaymentService`

Owns the order-payment transaction. It may use user, menu, order, and
idempotency repositories because they participate in one atomic use case.

The service performs these steps:

1. Acquire or read the shared idempotency record.
2. Return the stored response for a completed matching request.
3. Read the menu price.
4. Deduct points using an atomic conditional database update.
5. Create one paid order with the captured payment amount.
6. Store the completed idempotency response.
7. Create the order with `collectionStatus = PENDING`.
8. Publish an order-completed event only after the transaction commits.

### `OrderCollectionDeliveryService`

Owns collection-delivery attempts for completed orders. It sends a pending
order's `orderId`, `userId`, `menuId`, and `paymentAmount` to the collection
platform. Each attempt starts a new database transaction and obtains the
specific order only when it is still `PENDING`, using a pessimistic database
lock. The lock serializes normal concurrent attempts from multiple application
instances, so they do not make the same external call for one order at the
same time.

On success, it marks the order `SUCCEEDED`. On failure, it leaves the order
`PENDING` so a later scheduled attempt can retry it. Delivery is still
at-least-once: if the external platform accepts a request but the process or
database transaction fails before the `SUCCEEDED` state commits, a later
attempt can send the same order again. The collection platform must therefore
deduplicate requests by `orderId`.

### `OrderCollectionRetryScheduler`

Periodically finds `PENDING` orders and delegates delivery to
`OrderCollectionDeliveryService`. It is safe to run on every application
instance: the delivery service rechecks and pessimistically locks each pending
order before calling the external platform. Receiver-side deduplication by
`orderId` remains required for at-least-once recovery.

### `PopularMenuService`

Reads and combines daily Redis ZSETs for the seven completed `Asia/Seoul`
calendar dates immediately before the current date. It excludes the current
date, orders the combined scores by count descending and menu ID ascending, and
returns at most three menus.

MySQL remains the source of truth. When Redis is unavailable or a daily cache
key is missing or invalid, the service aggregates `coffee_order` rows for the
affected dates and rebuilds the ZSET projection before returning the result.

### `PopularMenuCacheUpdater`

Receives a committed order event and increments the order's menu member in the
corresponding daily Redis ZSET. It uses one Redis script to atomically create a
`SETNX` projection marker for the order ID and execute `ZINCRBY`; retried events
therefore cannot increment the same order twice. It must not update Redis for an
order whose database transaction rolls back.

## 4. Concurrency and Idempotency

The database coordinates requests from all application instances.

- Point charges use an atomic increment update.
- Payments use a conditional decrement update that succeeds only when the
  balance covers the menu price.
- The unique `(operation, idempotency_key)` constraint serializes duplicate
  mutations across instances.
- A matching completed idempotency record replays the stored response.
- A reused key with a different request hash is rejected.
- A committed order is projected to one daily Redis ZSET member exactly once.
- A collection-delivery attempt pessimistically locks the still-`PENDING`
  order row before its external call, preventing normal concurrent instances
  from delivering the same order simultaneously.

Idempotency prevents repeated requests from duplicating a side effect. The
atomic point update prevents different concurrent requests from losing updates
or overspending a balance. Both mechanisms are required.

Redis improves popular-menu read throughput but does not replace MySQL order
data. Cache recovery aggregates MySQL orders and restores the Redis projection.

## 5. Data Collection

`OrderPaymentService` creates the order with its durable delivery state inside
the payment transaction, then publishes an event after commit. The event
triggers an immediate best-effort attempt, while the retry scheduler provides
recovery if that call fails or the process stops. `DataCollectionClient` sends
the payload to a configured Mock API or test double.

```text
Order payment transaction commits
  -> OrderCompletedEvent
  -> OrderCollectionDeliveryService locks the PENDING order
  -> DataCollectionClient sends the locked order
  -> DataCollectionClient (orderId, userId, menuId, paymentAmount)
  -> Data collection platform

Scheduled retry
  -> selects PENDING order
  -> locks the still-PENDING order
  -> DataCollectionClient
  -> marks SUCCEEDED or leaves PENDING
```

A failed external call never rolls back a committed payment. The order row
retains its pending state for a later retry. The pessimistic lock prevents the
ordinary concurrent-instance duplicate-call path, but it cannot make an
external call and the database commit atomic. Delivery is therefore
at-least-once, and the collection platform must deduplicate requests by
`orderId`.
