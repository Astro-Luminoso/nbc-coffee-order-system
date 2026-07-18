# Coffee Order System Product Requirements Document

## 1. Project Goal

Build a reliable backend for a coffee ordering service. A user must be able to
list coffee menus, charge points, order and pay for one coffee, and view the
three most popular menus from the last seven days.

The application must remain correct when requests are handled by multiple
application instances. Point balances and paid-order counts must be accurate
under concurrent traffic.

## 2. Scope

The public API contains the following four features:

1. List coffee menus.
2. Charge user points.
3. Order and pay for one coffee menu.
4. List the top three popular menus from the last seven days.

Authentication and authorization are outside the scope of this assignment.
Each order contains exactly one menu because the order request accepts one
`menuId`. Cart, quantity, discount, refund, and inventory features are also
outside the scope.

## 3. Functional Requirements

### F-01. List Coffee Menus

- Return every menu with its ID, name, and price.
- Return an empty list when no menus are available.

### F-02. Charge Points

- Accept a user identifier and a positive charge amount.
- Convert one Korean won to one point (`1 KRW = 1 point`).
- Return the balance after the successful charge.
- Require an `Idempotency-Key` header. Repeating the same request with the
  same key must return the original result without charging points again.

### F-03. Order and Pay for Coffee

- Accept a user identifier and one menu identifier in a single request.
- Require an `Idempotency-Key` header.
- Use points as the only payment method.
- Deduct the current menu price and create one paid order in the same database
  transaction.
- Reject the request when the user has insufficient points. A rejection must
  not change the balance or create an order.
- Preserve the paid amount in the order so later menu-price changes do not
  change payment history.
- After a successful transaction commits, send `userId`, `menuId`, and
  `paymentAmount` to the configured data collection platform.

### F-04. List Popular Menus

- Return at most three menus ordered by paid-order count from the last seven
  days.
- Use one Redis ZSET for each calendar date in the `Asia/Seoul` timezone to
  cache menu order counts.
- Count the seven completed calendar dates immediately before the current date.
  The current date is excluded.
- Break equal counts by menu ID in ascending order.
- Keep MySQL paid orders as the source of truth. Rebuild a missing or invalid
  Redis cache from MySQL aggregation before returning a popularity result.

## 4. Consistency and Concurrency Requirements

- The application must not depend on process-local state for correctness.
- Idempotency records must be stored in shared durable storage so retries are
  safe across instances and process restarts.
- A user's point balance must never be negative.
- Point changes must use an atomic database update. Payment deduction must
  include a sufficient-balance condition.
- Point deduction, order creation, and completion of the corresponding
  idempotency record must commit or roll back together.
- A unique idempotency record prevents concurrent duplicate requests with the
  same operation and key from creating duplicate side effects.
- A committed order must increment its daily Redis ZSET score exactly once.
- Popular-menu cache recovery must aggregate committed MySQL orders, so Redis
  loss does not change the authoritative order counts.

## 5. Data Collection Boundary

The data collection platform is an external system. The application invokes a
configured client after a successful payment transaction commits. The payload
contains the user identifier, menu identifier, and payment amount.

A delivery failure is logged and does not roll back an already committed
payment. Guaranteed delivery and retry orchestration through a transactional
outbox are intentionally outside the baseline scope; they can be introduced
later if the external platform requires delivery guarantees.

The popular-menu Redis cache is a separate read-side concern. It is updated
only after a payment commits and is rebuilt from MySQL when it is unavailable,
lost, or detected as invalid.

## 6. Acceptance Criteria

- Menu listing returns menu ID, name, and price.
- A valid point charge increases the correct balance exactly once.
- A repeated point charge with the same idempotency key does not charge twice.
- A valid payment creates one order and deducts the exact menu price.
- A repeated payment with the same idempotency key returns the original order
  result without creating another order or deducting points again.
- Insufficient points create no order and leave the balance unchanged.
- Concurrent point operations do not lose updates or allow overspending.
- Popular-menu results contain exact counts for the seven completed calendar
  dates immediately before the current date, and contain at most three menus.
- Redis cache loss or invalidation rebuilds the affected popularity data from
  MySQL without changing returned counts.
- A successful payment invokes the data collection client after commit.
- Automated tests cover normal cases, validation failures, idempotent retries,
  concurrent point operations, payment consistency, popularity aggregation,
  and data-collection invocation.
