# Coffee Order System Product Requirements Document

## 1. Project Goal (Assignment)

The goal of this assignment is to build a reliable backend for a coffee ordering service.
A user must be able to browse coffee menus, charge points, order and pay for a coffee with those points,
and view the three most popular menus from the seven completed calendar dates immediately before the current date.

The solution must demonstrate more than basic API behavior.
It must remain correct when multiple application instances are running, protect point balances and order counts from concurrency issues,
preserve data consistency, and include tests for every feature and constraint.

### Success Criteria

- All mandatory features and their supporting order-attempt endpoint are implemented and behave according to this document.
- One Korean won is converted to one point (`1 KRW = 1 point`).
- Coffee purchases can be paid for only with points.
- Creating an order and deducting its payment amount are processed consistently.
- Every successfully paid order is sent to a data collection platform in near real time.
- The top three menus are calculated from exact paid-order counts for the seven completed calendar dates immediately before the current date in the `Asia/Seoul` business timezone. The current date is excluded.
- The application works correctly when multiple server instances run at the same time.
- Automated tests cover features, business rules, concurrency, and consistency constraints.
- The project `README.md` documents the ERD, API specification, design intent, problem-solving strategy and analysis, and reasons for technical choices.

## 2. User Scenarios

### Scenario 1: Browse Coffee Menus

1. A user requests the coffee menu list.
2. The system returns each menu's ID, name, and price.
3. The user selects a menu to order.

### Scenario 2: Charge Points

1. A user submits a user identifier and a positive charge amount.
2. The system converts the amount at `1 KRW = 1 point` and adds it to the user's point balance.
3. The system returns the updated point balance.
4. Concurrent charges must not cause any point update to be lost.

### Scenario 3: Order and Pay for Coffee

1. A user submits a user identifier and menu ID to create an order attempt.
2. The system stores the immutable request and returns a server-generated order-attempt identifier with `PENDING` status.
3. The client retains that identifier and submits it when the user confirms the purchase.
4. The system locks the attempt, verifies the menu and current price, and verifies that the user has enough points.
5. The system deducts the menu price, creates a paid order with one order item, creates a durable delivery task, and completes the attempt as one consistent business operation.
6. If the balance is insufficient or the transaction fails, the system leaves the attempt pending and does not deduct points or create a paid order.
7. Repeating confirmation of the completed attempt returns the original response without another deduction or order.
8. The system sends the user identifier, menu ID, and payment amount to a data collection platform in near real time.

### Scenario 4: View Popular Menus

1. A user requests the popular-menu list.
2. The system counts successfully paid orders for each menu from the seven completed calendar dates immediately before the current date.
3. The system returns up to three menus ordered by order count from highest to lowest.
4. The result must remain accurate across multiple application instances and concurrent orders.

## 3. Tech Stack

The dependency versions and libraries in `build.gradle` are the source of truth for the application stack.

| Area                  | Technology                                   | Purpose                                              |
|-----------------------|----------------------------------------------|------------------------------------------------------|
| Language              | Java 21                                      | Application implementation                           |
| Build                 | Gradle Wrapper                               | Reproducible builds and test execution               |
| Framework             | Spring Boot 4.1.0                            | Application configuration and runtime                |
| API                   | Spring Web MVC                               | HTTP API implementation                              |
| Validation            | Spring Validation                            | Request and business-input validation                |
| Persistence           | Spring Data JPA                              | Relational data access                               |
| Primary database      | MySQL 8.4                                    | Users, menus, orders, and order items                |
| Cache                 | Spring Data Redis / Redis 7.4 Alpine         | Search-result caching and popular-menu data          |
| Operations            | Spring Boot Actuator                         | Health and operational endpoints                     |
| Development database  | H2                                           | Lightweight development or test support              |
| Testing               | JUnit Platform and Spring Boot test starters | Unit, integration, concurrency, and constraint tests |
| Boilerplate reduction | Lombok                                       | Java model and component boilerplate reduction       |
| Local infrastructure  | Docker                                       | Local Redis container                                |

The application must not depend on in-memory state for correctness. Shared MySQL and Redis infrastructure must support multiple application instances.

## 4. Domain Model

### Core Models

| Model             | Key Attributes                                                   | Responsibilities and Constraints                                                                                                                             |
|-------------------|------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| User              | `id`, `balance`                                                  | Identifies the customer, stores the current point balance, and places orders. The balance must never be negative, and concurrent updates must be controlled. |
| Menu              | `id`, `name`, `price`                                            | Represents a coffee available for ordering. The price must be positive.                                                                                      |
| Order             | `id`, `userId`, `totalAmount`, `orderedAt`                       | Represents a completed POS sale. An order is persisted only after point payment succeeds and contains one or more order items.                               |
| Order Item        | `id`, `orderId`, `menuId`, `quantity`, `unitPrice`, `lineAmount` | Represents a purchased menu line and preserves its price at payment time. The current API creates exactly one item with quantity `1`.                        |
| Popular Menu View | Daily ZSET key, `menuId` member, paid-order-count score, key TTL | Redis-derived read model for ranking menus. It is not an RDBMS table and must remain rebuildable from paid order items in MySQL.                             |

### Popular Menu View Storage Model

The popular-menu view is stored as one Redis ZSET for each calendar date in the `Asia/Seoul` business timezone.

| Redis Element | Format                      | Meaning                                                              |
|---------------|-----------------------------|----------------------------------------------------------------------|
| Key           | `popular-menu:{yyyy-MM-dd}` | Daily popularity bucket, such as `popular-menu:2026-07-10`           |
| Type          | ZSET                        | Maintains members ordered by their numeric scores                    |
| Member        | `menuId`                    | Stable menu identifier; menu names and prices remain in MySQL        |
| Score         | Paid order count            | Number of successfully paid orders for the menu on that date         |
| TTL           | Daily ZSET key expiration   | Removes the entire expired daily bucket after it is no longer needed |

For example, if menu `1` is ordered successfully, the application increments its score in that date's key:

```redis
ZINCRBY popular-menu:2026-07-10 1 menu:1
```

The top-three query combines the seven completed date buckets immediately before the current date, sums scores by `menuId`, and returns the three members with the highest combined scores. The current date's ZSET is not included. The API resolves each member's menu name and other display information from the MySQL `Menu` data.

TTL applies to each daily ZSET key, not to individual ZSET members. Each bucket must expire at a fixed time after the seven-day query window so that it cannot disappear while still participating in a valid ranking query.

### Relationships

- A user stores one current point balance.
- A user can place many orders.
- An order contains one or more order items. The current API creates exactly one order item per order.
- A menu can be referenced by many order items.
- The popular-menu view is a Redis projection derived from MySQL paid order items grouped by date and menu.
- A daily ZSET contains many menus, and the same menu may appear in each date bucket in which it was ordered.

### Business Rules

- Points are the only supported payment method.
- A charge amount and menu price must be positive.
- A user's point balance must never fall below zero.
- An order item's quantity, unit price, and line amount must be positive.
- An order item's line amount equals its unit price multiplied by its quantity.
- An order's total amount equals the sum of its order-item line amounts.
- The current API accepts one `menuId`, so it creates one order item with quantity `1`.
- Point deduction, order creation, and order-item creation must be atomic from the user's perspective.
- Only successfully paid orders are persisted.
- Concurrent charges and payments must not lose updates or allow overspending.
- A point-charge retry with the same client-generated idempotency key and request must return the original response without charging again.
- The server generates an order-attempt identifier before confirmation and stores the immutable `userId` and `menuId` request with `PENDING` status.
- A client retains the order-attempt identifier until the order completes or the attempt expires; confirmation does not accept mutable order data.
- Concurrent or repeated confirmations of one order attempt create at most one paid order. A completed confirmation returns its original response.
- A failed confirmation leaves the attempt `PENDING` and causes no point, order, item, or outbox side effect.
- The application sends the user ID, menu ID, and payment amount from the completed order to the data collection platform in near real time. This transmission is application behavior and does not add another core domain entity.
- Every committed order creates one durable outbox task that references the order. A worker reconstructs the payload from immutable order data and retries delivery at least once until acknowledged, providing eventual delivery when the platform becomes available.
- A delivery failure after the order transaction commits does not roll back the order or change its `201 Created` response. The collector deduplicates retries by `orderId`.
- Popularity uses only successfully paid orders from the seven completed calendar dates immediately before the current date in the `Asia/Seoul` business timezone.
- A successful payment increments the corresponding `menuId` score in that date's ZSET exactly once.
- Redis `ZINCRBY` operations and duplicate-safe popularity updates must prevent lost or duplicated counts across application instances.
- MySQL paid orders and order items are the source of truth; daily ZSETs must be rebuildable or reconcilable if Redis data is lost or inconsistent.
- Popular-menu counts must be consistent across all running application instances.
- When order counts are tied, a deterministic secondary ordering must be defined in the API specification.

## 5. Feature List

### Mandatory Features

| ID   | Feature                       | Requirements                                                                                                              | Acceptance Criteria                                                                                                                                                                                                                                                                                                                                                     |
|------|-------------------------------|---------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| F-01 | Coffee menu list              | Provide coffee menu ID, name, and price.                                                                                  | A request returns all available menus with the required fields.                                                                                                                                                                                                                                                                                                         |
| F-02 | Point charge                  | Accept a user identifier and charge amount; apply `1 KRW = 1 point`.                                                      | A valid charge increases the correct user's balance exactly once and returns the resulting balance.                                                                                                                                                                                                                                                                     |
| F-03 | Coffee order and payment      | Create a server-managed order attempt from a user identifier and menu ID, then confirm it and pay only with points.       | A successful confirmation returns `201 Created` only after atomically deducting the exact menu price, creating one order item of quantity `1`, creating its outbox task, and completing the attempt. Retrying the completed attempt returns the original response. An invalid menu, insufficient balance, or rolled-back transaction creates no paid-order side effect. |
| F-04 | Real-time order data delivery | Send the user identifier, menu ID, and payment amount to a data collection platform.                                      | Each successfully paid order creates one durable task. Delivery is at least once and retried until acknowledged; the collector deduplicates by `orderId`. A post-commit delivery failure does not change the order response or roll back persisted order data.                                                                                                          |
| F-05 | Popular menu list             | Return the three most ordered menus across the seven completed calendar-date buckets immediately before the current date. | The response excludes the current date, combines the preceding seven daily ZSETs, and contains at most three menus based on exact paid-order counts.                                                                                                                                                                                                                    |

### Quality and Delivery Features

| ID   | Feature                  | Requirements                                                                                                                  | Acceptance Criteria                                                                                                                                                                                                                                                                                                              |
|------|--------------------------|-------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Q-01 | Multi-instance operation | Avoid correctness dependencies on a single application process.                                                               | The mandatory features behave correctly when requests are distributed across multiple instances.                                                                                                                                                                                                                                 |
| Q-02 | Concurrency control      | Protect point balances, orders, and popularity counts under concurrent requests.                                              | Concurrency tests prove that balances cannot overspend, updates are not lost, and counts remain correct.                                                                                                                                                                                                                         |
| Q-03 | Data consistency         | Define transaction boundaries, idempotent retry behavior, and external-data delivery behavior.                                | Tests prove that pre-commit failures leave no paid-order side effects, post-commit delivery failures preserve the order and `201` response, repeated confirmation does not duplicate payment or order data, and delivery retries are safe by `orderId`.                                                                          |
| Q-04 | Automated testing        | Test all features and constraints.                                                                                            | Unit and integration tests cover normal cases, validation errors, insufficient points, point-charge key reuse, pending and completed order attempts, concurrent confirmation and response replay, daily-bucket aggregation and TTL boundaries, post-commit delivery failures, retries, and collector deduplication by `orderId`. |
| Q-05 | Design documentation     | Provide the ERD, API specification, design intent, analyzed solution strategy, and technical-choice rationale in `README.md`. | Reviewers can trace each implementation decision back to an explicit requirement and design reason.                                                                                                                                                                                                                              |

HTTP methods, error-response formats, and application-specific error codes may be selected during API design, but they must be consistent and documented.
