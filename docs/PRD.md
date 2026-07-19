# Coffee Order System Product Requirements Document

## 1. Project Goal (Assignment)

The goal of this assignment is to build a reliable backend for a coffee
ordering service. A user must be able to browse coffee menus, charge points,
order and pay for a coffee with those points, and view the three most popular
menus from the seven completed calendar dates immediately before the current
date.

The solution must demonstrate more than basic API behavior. It must remain
correct when multiple application instances are running, protect point balances
and order counts from concurrency issues, preserve data consistency, and
include tests for every feature and constraint.

### Success Criteria

- The four mandatory APIs are implemented and behave according to this
  document.
- One Korean won is converted to one point (`1 KRW = 1 point`).
- Coffee purchases can be paid for only with points.
- Creating an order and deducting its payment amount are processed
  consistently.
- Point charges and payments can be retried safely with an idempotency key.
- Every successfully paid order is recorded as pending delivery in the payment
  transaction and reconciled with the MockAPI.io `orders` resource after
  commit. Failed delivery is retried without rolling back the payment.
- The top three menus are calculated from exact paid-order counts for the seven
  completed calendar dates immediately before the current date in the
  `Asia/Seoul` business timezone. The current date is excluded.
- The application works correctly when multiple server instances run at the
  same time.
- Automated tests cover features, business rules, concurrency, and consistency
  constraints.

## 2. User Scenarios

### Scenario 1: Browse Coffee Menus

1. A user requests the coffee menu list.
2. The system returns each menu's ID, name, and price.
3. The user selects a menu to order.

### Scenario 2: Charge Points

1. A user submits a user identifier, a positive charge amount, and an
   idempotency key.
2. The system converts the amount at `1 KRW = 1 point` and adds it to the
   user's point balance.
3. The system returns the updated point balance.
4. Repeating the same request with the same key returns the original result
   without adding points again.
5. Concurrent charges must not cause any point update to be lost.

### Scenario 3: Order and Pay for Coffee

1. A user submits a user identifier, menu ID, and idempotency key.
2. The system verifies the menu and its current price, then verifies that the
   user has enough points.
3. The system deducts the menu price, creates one paid order, and completes the
   idempotency record as one consistent business operation.
4. If the balance is insufficient or the transaction fails, the system does not
   deduct points or create a paid order.
5. Repeating a completed request with the same key returns the original
   response without another deduction or order.
6. The payment transaction creates the order with a pending collection-delivery
   state.
7. After commit, the system attempts to send the order ID, user identifier,
   menu ID, and payment amount to the MockAPI.io `orders` resource in near real
   time.
8. Each delivery attempt locks only the still-pending order before it calls
   MockAPI.io. A failed attempt leaves the order pending, and a shared scheduler
   retries it without creating another local order or charging points again.
9. The client first filters the MockAPI.io `orders` resource by `orderId`. A
   matching record with the same payload completes the delivery; no match
   causes the client to create the record with `POST /orders`.
10. Multiple matches or a matching `orderId` with different order data are
    treated as an external-data conflict and leave the local delivery pending.

### Scenario 4: View Popular Menus

1. A user requests the popular-menu list.
2. The system combines Redis popularity counts for the seven completed calendar
   dates immediately before the current date.
3. The system returns up to three menus ordered by order count from highest to
   lowest.
4. If a Redis popularity cache is missing or invalid, the system rebuilds it
   from committed MySQL orders before returning the result.
5. The result must remain accurate across multiple application instances and
   concurrent orders.

## 3. Tech Stack

The dependency versions and libraries in `build.gradle` are the source of truth
for the application stack.

| Area | Technology | Purpose |
|---|---|---|
| Language | Java 21 | Application implementation |
| Build | Gradle Wrapper | Reproducible builds and test execution |
| Framework | Spring Boot 4.1.0 | Application configuration and runtime |
| API | Spring Web MVC | HTTP API implementation |
| Validation | Spring Validation | Request and business-input validation |
| Persistence | Spring Data JPA | Relational data access |
| Primary database | MySQL 8.4 | Users, menus, orders, and idempotency records |
| Cache | Spring Data Redis / Redis 7.4 Alpine | Daily popular-menu ZSET cache |
| External API | MockAPI.io | REST resource for collected paid-order data |
| Operations | Spring Boot Actuator | Health and operational endpoints |
| Development database | H2 | Lightweight development or test support |
| Testing | JUnit Platform and Spring Boot test starters | Unit, integration, concurrency, and constraint tests |
| Boilerplate reduction | Lombok | Java model and component boilerplate reduction |
| Local infrastructure | Docker | Local Redis container |

The application must not depend on in-memory state for correctness. Shared
MySQL and Redis infrastructure must support multiple application instances.

## 4. Domain Model

### Core Models

| Model | Key Attributes | Responsibilities and Constraints |
|---|---|---|
| User | `id`, `pointBalance` | Identifies the customer, stores the current point balance, and places orders. The balance must never be negative. |
| Menu | `id`, `name`, `price` | Represents a coffee available for ordering. The price must be positive. |
| Order | `id`, `userId`, `menuId`, `paymentAmount`, `orderedAt`, delivery state, popularity-projection state | Represents one completed coffee purchase, preserves the price paid at payment time, and owns its collection-delivery and Redis-projection lifecycles. |
| Idempotency Record | `operation`, `idempotencyKey`, `requestHash`, `status`, `responseBody` | Prevents a point charge or payment retry from creating duplicate side effects across instances. |
| Popular Menu View | Daily ZSET key, `menuId` member, paid-order-count score | Redis-derived cache for ranking menus. MySQL orders remain the source of truth. |

### Popular Menu View Storage Model

The popular-menu view is stored as one Redis ZSET for each calendar date in the
`Asia/Seoul` business timezone.

| Redis Element | Format | Meaning |
|---|---|---|
| Key | `popular-menu:{yyyy-MM-dd}` | Daily popularity bucket, such as `popular-menu:2026-07-10` |
| Type | ZSET | Maintains members ordered by their numeric scores |
| Member | `menu:{menuId}` | Stable menu identifier; menu names and prices remain in MySQL |
| Score | Paid order count | Number of successfully paid orders for the menu on that date |
| Projection marker | `popular-menu:projection:{orderId}` | Prevents a retried projection from incrementing the same order twice |
| Source of truth | `coffee_order` rows in MySQL | Data used to rebuild a missing or invalid cache |

After a payment commits, the application atomically creates the order's
projection marker and increments the matching daily ZSET score. The order keeps
a pending popularity-projection state until this Redis projection is confirmed,
so a failed projection can be retried without double-counting. The top-three
query combines the seven completed calendar-date buckets immediately before the
current date. The current date's ZSET is not included. If a required key is
missing, unavailable, or invalid, the application aggregates MySQL orders and
rebuilds the cache before returning the result.

### Relationships

- A user stores one current point balance.
- A user can place many orders.
- Each order references exactly one menu because the current API accepts one
  `menuId`.
- A menu can be referenced by many orders.
- The popular-menu view is a Redis cache derived from MySQL paid orders grouped
  by date and menu.
- A daily ZSET contains many menus, and the same menu may appear in each date
  bucket in which it was ordered.

### Business Rules

- Points are the only supported payment method.
- A charge amount and menu price must be positive.
- A user's point balance must never fall below zero.
- An order's payment amount is positive and captures the menu price at payment
  time.
- The current API accepts one `menuId`, so one paid order represents one menu
  purchase.
- Point deduction, order creation, and idempotency completion must be atomic
  from the user's perspective.
- Only successfully paid orders are persisted.
- Concurrent charges and payments must not lose updates or allow overspending.
- A point-charge or payment retry with the same client-generated idempotency
  key and request must return the original response without another side effect.
- Reusing an idempotency key with a different request is rejected.
- The order is created with a durable pending collection-delivery state in the
  completed payment transaction. A first delivery attempt occurs after commit;
  a delivery failure does not roll back the payment and is retried later.
- Each delivery attempt locks the still-pending order in a new database
  transaction before its external call. This prevents normal concurrent
  instances from delivering the same order at the same time.
- The external collection payload includes the order ID as the reconciliation
  identifier, in addition to the user ID, menu ID, and payment amount.
- Before creating a MockAPI.io record, the client queries
  `GET /orders?orderId={orderId}`. One matching record with the same order data
  is treated as an already completed delivery; an empty result causes
  `POST /orders`.
- A failed collection delivery remains pending until a later attempt succeeds.
  The external call and the local `SUCCEEDED` commit are not atomic, so a retry
  always performs the lookup before it creates a record. MockAPI.io does not
  provide the local database transaction or its `orderId` uniqueness guarantee;
  manual or unrelated external writes remain outside this application's
  consistency boundary.
- Popularity uses only successfully paid orders from the seven completed
  calendar dates immediately before the current date in the `Asia/Seoul`
  business timezone.
- A committed order increments its daily Redis ZSET score exactly once.
- MySQL paid orders are the source of truth. Redis daily ZSETs are rebuilt from
  MySQL when cache data is missing, unavailable, or invalid.
- Popular-menu counts must be consistent across all running application
  instances.
- When order counts are tied, the API orders menus by menu ID ascending.

## 5. Feature List

### Mandatory Features

| ID | Feature | Requirements | Acceptance Criteria |
|---|---|---|---|
| F-01 | Coffee menu list | Provide coffee menu ID, name, and price. | A request returns all available menus with the required fields. |
| F-02 | Point charge | Accept a user identifier and charge amount; apply `1 KRW = 1 point`; require an idempotency key. | A valid charge increases the correct user's balance exactly once. Replaying the same key and request returns the original result. |
| F-03 | Coffee order and payment | Accept a user identifier and menu ID in one request; require an idempotency key; pay only with points. | A successful request atomically deducts the exact current menu price, creates one order, and stores its idempotency result. A retry creates no duplicate order or deduction. |
| F-04 | Real-time order data delivery | Persist a pending delivery status with each successful order, then reconcile the order ID, user identifier, menu ID, and payment amount with MockAPI.io after commit. | A delivery attempt locks the still-pending order, finds an existing MockAPI.io record by `orderId`, and creates one only when no match exists. A failure does not roll back persisted order data. |
| F-05 | Popular menu list | Return the three most ordered menus across the seven completed calendar-date buckets immediately before the current date. | The response excludes the current date, aggregates cached Redis daily counts, and rebuilds missing or invalid cache data from MySQL without changing returned counts. |

### Quality and Delivery Features

| ID | Feature | Requirements | Acceptance Criteria |
|---|---|---|---|
| Q-01 | Multi-instance operation | Avoid correctness dependencies on a single application process. | The mandatory features behave correctly when requests are distributed across multiple instances. |
| Q-02 | Concurrency control | Protect point balances, idempotent mutations, and popularity counts under concurrent requests. | Tests prove that balances cannot overspend, updates are not lost, duplicate requests do not repeat side effects, and counts remain correct. |
| Q-03 | Data consistency | Define transaction boundaries, idempotent retry behavior, Redis projection behavior, and external-data delivery behavior. | Tests prove that rolled-back transactions leave no paid-order side effects, a completed retry does not duplicate payment or order data, Redis recovery rebuilds counts from MySQL, and delivery status survives a failed send. |
| Q-04 | Automated testing | Test all features and constraints. | Unit and integration tests cover normal cases, validation errors, insufficient points, idempotency-key reuse, concurrent requests, popularity aggregation, Redis recovery, MockAPI.io lookup and creation, successful and failed collection delivery, and same-order delivery contention. |
| Q-05 | Design documentation | Document ERD, API specification, design intent, problem-solving strategy, and technical choices in the project documentation. | Reviewers can trace each implementation decision back to an explicit requirement and design reason. |

HTTP methods, error-response formats, and application-specific error codes may
be selected during API design, but they must be consistent and documented.
