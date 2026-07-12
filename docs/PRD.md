# Coffee Order System Product Requirements Document

## 1. Project Goal (Assignment)

The goal of this assignment is to build a reliable backend for a coffee ordering service. 
A user must be able to browse coffee menus, charge points, order and pay for a coffee with those points, 
and view the three most popular menus from the last seven days.

The solution must demonstrate more than basic API behavior. 
It must remain correct when multiple application instances are running, protect point balances and order counts from concurrency issues, 
preserve data consistency, and include tests for every feature and constraint.

### Success Criteria

- All four mandatory APIs are implemented and behave according to this document.
- One Korean won is converted to one point (`1 KRW = 1 point`).
- Coffee purchases can be paid for only with points.
- Creating an order and deducting its payment amount are processed consistently.
- Every successfully paid order is sent to a data collection platform in near real time.
- The top three menus are calculated from exact paid-order counts for the current calendar date and the previous six dates in the configured business timezone.
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

1. A user submits a user identifier and menu ID.
2. The system verifies that the menu exists and obtains its current price.
3. The system verifies that the user has enough points.
4. The system deducts the menu price and creates a paid order as one consistent business operation.
5. If the balance is insufficient, the system rejects the request without deducting points or creating a paid order.
6. The system sends the user identifier, menu ID, and payment amount to a data collection platform in near real time.
7. A transmission failure must not invalidate an already completed payment, and the event must remain retryable.

### Scenario 4: View Popular Menus

1. A user requests the popular-menu list.
2. The system counts successfully paid orders for each menu from the current calendar date and the previous six dates.
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
| Primary database      | MySQL 8.4                                    | Menus, users, points, transactions, and orders       |
| Cache                 | Spring Data Redis / Redis 7.4 Alpine         | Search-result caching and popular-menu data          |
| Security              | Spring Security                              | API security foundation                              |
| Operations            | Spring Boot Actuator                         | Health and operational endpoints                     |
| Development database  | H2                                           | Lightweight development or test support              |
| Testing               | JUnit Platform and Spring Boot test starters | Unit, integration, concurrency, and constraint tests |
| Boilerplate reduction | Lombok                                       | Java model and component boilerplate reduction       |
| Local infrastructure  | Docker                                       | Local MySQL and Redis containers                     |

The application must not depend on in-memory state for correctness. Shared MySQL and Redis infrastructure must support multiple application instances.

## 4. Domain Model

### Core Models

| Model             | Key Attributes                                                                        | Responsibilities and Constraints                                                                                            |
|-------------------|---------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| User              | `id`, `balance`                                                                       | Identifies the customer, stores the current point balance, and places orders. The balance must never be negative, and concurrent updates must be controlled. |
| Point Transaction | `id`, `userId`, `type`, `amount`, `orderId`, `createdAt`                              | Records every charge and payment adjustment as an auditable history. The amount must be positive.                           |
| Menu              | `id`, `name`, `price`                                                                 | Represents a coffee available for ordering. The price must be positive.                                                     |
| Order             | `id`, `userId`, `menuId`, `paymentAmount`, `status`, `orderedAt`                      | Captures the purchased menu and price at the time of payment. Only successfully paid orders contribute to popularity.       |
| Order Event       | `id`, `orderId`, `userId`, `menuId`, `paymentAmount`, `status`, `createdAt`, `sentAt` | Tracks delivery of paid-order data to the external collection platform and supports retries and duplicate-safe processing.  |
| Popular Menu View | Daily ZSET key, `menuId` member, paid-order-count score, key TTL                      | Redis-derived read model for ranking menus. It is not an RDBMS table and must remain rebuildable from paid orders in MySQL. |

### Popular Menu View Storage Model

The popular-menu view is stored as one Redis ZSET for each calendar date in the configured business timezone.

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

The top-three query combines the current date's ZSET and the previous six date buckets, sums scores by `menuId`, and returns the three members with the highest combined scores. The API resolves each member's menu name and other display information from the MySQL `Menu` data.

TTL applies to each daily ZSET key, not to individual ZSET members. Each bucket must expire at a fixed time after the seven-day query window so that it cannot disappear while still participating in a valid ranking query.

### Relationships

- A user stores one current point balance.
- A user has many point transactions and orders.
- A menu can be referenced by many orders.
- A paid order has one payment point transaction and one order event.
- The popular-menu view is a Redis projection derived from MySQL paid orders grouped by date and menu.
- A daily ZSET contains many menus, and the same menu may appear in each date bucket in which it was ordered.

### Business Rules

- Points are the only supported payment method.
- A charge amount and menu price must be positive.
- A user's point balance must never fall below zero.
- Point deduction and paid-order creation must be atomic from the user's perspective.
- Concurrent charges and payments must not lose updates or allow overspending.
- Order-event delivery must be retryable and identifiable so duplicate delivery does not create duplicate data.
- Popularity uses only successfully paid orders from the current calendar date and the previous six dates in the configured business timezone.
- A successful payment increments the corresponding `menuId` score in that date's ZSET exactly once.
- Redis `ZINCRBY` operations and duplicate-safe order-event handling must prevent lost or duplicated counts across application instances.
- MySQL paid orders are the source of truth; daily ZSETs must be rebuildable or reconcilable if Redis data is lost or inconsistent.
- Popular-menu counts must be consistent across all running application instances.
- When order counts are tied, a deterministic secondary ordering must be defined in the API specification.

## 5. Feature List

### Mandatory Features

| ID | Feature | Requirements | Acceptance Criteria |
| --- | --- | --- | --- |
| F-01 | Coffee menu list | Provide coffee menu ID, name, and price. | A request returns all available menus with the required fields. |
| F-02 | Point charge | Accept a user identifier and charge amount; apply `1 KRW = 1 point`. | A valid charge increases the correct user's balance exactly once and returns the resulting balance. |
| F-03 | Coffee order and payment | Accept a user identifier and menu ID; pay only with points. | A successful request deducts the exact menu price and creates a paid order. An invalid menu or insufficient balance produces no paid order or deduction. |
| F-04 | Real-time order data delivery | Send the user identifier, menu ID, and payment amount to a data collection platform. | Each paid order produces a retryable delivery record and is transmitted in near real time without blocking permanent payment completion on a temporary external failure. |
| F-05 | Popular menu list | Return the three most ordered menus across the latest seven calendar-date buckets. | The response combines the seven daily ZSETs and contains at most three menus based on exact paid-order counts. |

### Quality and Delivery Features

| ID | Feature | Requirements | Acceptance Criteria |
| --- | --- | --- | --- |
| Q-01 | Multi-instance operation | Avoid correctness dependencies on a single application process. | The mandatory features behave correctly when requests are distributed across multiple instances. |
| Q-02 | Concurrency control | Protect point balances, orders, and popularity counts under concurrent requests. | Concurrency tests prove that balances cannot overspend, updates are not lost, and counts remain correct. |
| Q-03 | Data consistency | Define transaction boundaries and external-event delivery behavior. | Tests prove that partial failures do not leave contradictory point, order, or event state. |
| Q-04 | Automated testing | Test all features and constraints. | Unit and integration tests cover normal cases, validation errors, insufficient points, concurrency, daily-bucket aggregation and TTL boundaries, and event-delivery failures. |
| Q-05 | Design documentation | Provide the ERD, API specification, design intent, analyzed solution strategy, and technical-choice rationale in `README.md`. | Reviewers can trace each implementation decision back to an explicit requirement and design reason. |

HTTP methods, error-response formats, and application-specific error codes may be selected during API design, but they must be consistent and documented.
