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
7. Publish an order-completed event only after the transaction commits.

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

Idempotency prevents repeated requests from duplicating a side effect. The
atomic point update prevents different concurrent requests from losing updates
or overspending a balance. Both mechanisms are required.

Redis improves popular-menu read throughput but does not replace MySQL order
data. Cache recovery aggregates MySQL orders and restores the Redis projection.

## 5. Data Collection

`OrderPaymentService` publishes an event after a payment transaction commits.
`DataCollectionClient` receives that event and sends the required payload to a
configured Mock API or test double.

```text
Order payment transaction commits
  -> OrderCompletedEvent
  -> DataCollectionClient
  -> Data collection platform
```

The baseline implementation does not roll back a committed payment when the
external call fails. It records the failure in Korean operational logs. A
transactional outbox is a future extension for systems that require guaranteed
external delivery.
