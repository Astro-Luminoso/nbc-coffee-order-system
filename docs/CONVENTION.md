# Coffee Order System Conventions

## 1. HTTP Conventions

- All public API endpoints use the `/api/v1` base path.
- Request and response bodies use `application/json`.
- Field names use `camelCase`.
- Domain identifiers are positive 64-bit integers. The server-generated `orderAttemptId` is the documented string exception.
- Prices, charges, balances, and payment amounts are integer points. One Korean won equals one point.
- Date-time values use ISO 8601 with a UTC offset, such as `2026-07-13T14:30:00+09:00`.
- The business timezone is `Asia/Seoul`. Calendar-date windows and Redis daily keys must use dates resolved in this timezone.
- Unknown JSON request fields are ignored for forward compatibility.
- Authentication and authorization are outside the current API scope.

## 2. Idempotency Convention

Idempotency records are stored in shared durable storage so retries remain safe across application instances and process restarts. The `operation` and `idempotency_key` columns form the record identity. Supported operations are `POINT_CHARGE` and `ORDER_ATTEMPT`.

### Point Charge Keys

- Every point-charge request requires a client-generated `Idempotency-Key` request header.
- The value must be a non-blank ASCII string between 1 and 128 characters. A UUID is recommended.
- The idempotency scope consists of the normalized operation and key. The normalized endpoint path, path parameters, and request body form the canonical request stored with the record.
- A SHA-256 hash of the canonical request is stored as a 64-character lowercase hexadecimal value.
- A successful point charge and its `COMPLETED` idempotency result must commit in the same database transaction.
- Repeating the same request with the same key returns the original HTTP status and response body without repeating a side effect.
- Reusing the same key for a different canonical request returns `409 Conflict` with `IDEMPOTENCY_KEY_REUSED`.

### Order Attempt Keys

- The order-attempt creation endpoint generates the `orderAttemptId`; a client does not send an `Idempotency-Key` for order confirmation.
- The server stores the submitted `userId` and `menuId` as the immutable canonical request of a `PENDING` `ORDER_ATTEMPT` record.
- The client retains the returned `orderAttemptId` until the order is completed or the attempt expires. It may synchronize this value across browser sessions.
- Confirmation contains no mutable order request body. The server loads and locks the attempt record before applying business logic.
- Concurrent confirmations of one `PENDING` attempt are serialized. Exactly one transaction may deduct points, create the order and delivery task, and change the attempt to `COMPLETED`.
- A `COMPLETED` attempt stores its `order_id`, original `201` status, response body, and completion time. Repeating confirmation returns that exact result.
- Validation, business, and transaction failures do not change the attempt from `PENDING`, so the same attempt may be confirmed again while it remains valid.
- An expired `PENDING` attempt cannot be confirmed and returns `410 Gone` with `ORDER_ATTEMPT_EXPIRED`.

### Storage and Retention

- A `PENDING` record has no `order_id`, HTTP result, response body, or completion time. This is why `order_id` is physically nullable.
- A `COMPLETED` `ORDER_ATTEMPT` record must have an `order_id`; a `POINT_CHARGE` record never has one.
- Every `COMPLETED` record must have its original HTTP status, response body, and completion time.
- A successful mutation and the transition to `COMPLETED` must commit in the same database transaction.
- A completed result must be retained for at least 24 hours after `completed_at`; completion updates `expires_at` when necessary to preserve that minimum. A pending attempt is retained until its declared `expires_at`.
- Expiration cleanup must not remove a record before its applicable retention period ends.

Idempotency protects clients when an HTTP response is lost after a successful commit. It does not change validation or business rules.

## 3. Common API Response

Every endpoint returns a `CommonApiResponse<T>` body containing the HTTP status and an endpoint-specific response DTO in `data`.

```java
/**
 * API 공통 응답을 표현한다.
 *
 * @param httpStatus HTTP 상태 코드
 * @param data 응답 데이터
 * @param <T> 응답 데이터 타입
 */
public record CommonApiResponse<T>(
        int httpStatus,
        T data
) {
    /**
     * HTTP 상태 코드와 응답 데이터로 공통 응답을 생성한다.
     *
     * @param httpStatus HTTP 상태 코드
     * @param data 응답 데이터
     * @param <T> 응답 데이터 타입
     * @return 생성된 공통 API 응답
     */
    public static <T> CommonApiResponse<T> of(int httpStatus, T data) {
        return new CommonApiResponse<>(httpStatus, data);
    }
}
```

The `httpStatus` value uses the numeric HTTP status code, such as `200`, `201`, or `400`.
It must match the HTTP response status line. Clients must use the HTTP response status line as the authoritative transport status.

Example success response:

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "httpStatus": 200,
  "data": {
    "menus": []
  }
}
```

## 4. Request DTO Conventions

- Every API request DTO must be declared as a Java `record`.
- Every request DTO component must declare the applicable constraints from the Jakarta Validation dependency (for example, `@NotNull`, `@NotBlank`, `@Positive`, `@Size`, or `@Pattern`). Do not defer client-input validation solely to service-layer code.
- Controllers must apply `@Valid` to each request DTO parameter so that validation occurs before the application processes the request.
- Constraint choices and messages must match the API contract. A request whose values violate its declared constraints returns `400 Bad Request` through `GlobalExceptionHandler`.

## 5. Response DTO Conventions

- Every response DTO must be declared as a Java `record`.
- Response DTOs must contain API-facing values only and must not expose JPA entities.
- A collection response must return an empty array instead of `null` when no items exist.
- A response field that is required by the API contract must not be `null`.
- The common response wrapper and nested error responses are also records.
- Response DTOs must provide static factory methods and must be created through those methods outside the DTO.
- Use `from(...)` when converting one source object and `of(...)` when composing a response from multiple values or source objects.
- Mapping and response-construction rules belong in the static factory method rather than in controllers.

Example endpoint response DTO:

```java
/**
 * 메뉴 목록 응답을 표현한다.
 *
 * @param menus 메뉴 응답 목록
 */
public record MenuListResponse(
        List<MenuResponse> menus
) {
    /**
     * 메뉴 목록을 API 응답으로 변환한다.
     *
     * @param menus 변환할 메뉴 목록
     * @return 생성된 메뉴 목록 응답
     */
    public static MenuListResponse from(List<Menu> menus) {
        return new MenuListResponse(
                menus.stream()
                        .map(MenuResponse::from)
                        .toList()
        );
    }

    /**
     * 단일 메뉴 응답을 표현한다.
     *
     * @param id 메뉴 식별자
     * @param name 메뉴 이름
     * @param price 메뉴 가격
     */
    public record MenuResponse(
            Long id,
            String name,
            Long price
    ) {
        /**
         * 메뉴를 API 응답으로 변환한다.
         *
         * @param menu 변환할 메뉴
         * @return 생성된 메뉴 응답
         */
        public static MenuResponse from(Menu menu) {
            return new MenuResponse(
                    menu.getId(),
                    menu.getName(),
                    menu.getPrice()
            );
        }
    }
}
```

## 6. Java Code Conventions

- Lombok `@Getter`, `@Setter`, and `@Builder` annotations are prohibited.
- Classes must declare explicit methods only for behavior and access that the application actually requires.
- Every class, record, interface, enum, and method must have Javadoc written in Korean.
- All line comments (`//`) and block comments (`/* ... */`) written by the project must be in Korean.
- Javadoc tags such as `@param`, `@return`, and `@throws` remain in their standard form, but their descriptions must be in Korean.

## 7. Exception Handling

All application failures must be represented by a situation-specific custom exception that extends `ServiceException`.
`ServiceException` extends `RuntimeException` and has the following properties:

- `message`: The client-safe failure message. It is passed to and obtained from `RuntimeException`.
- `httpStatus`: The `HttpStatus` returned to the client.

`ServiceException` must expose its `HttpStatus` through an explicit accessor. It must not use a generic application error-code property. The error response `code` is derived from the custom exception class name by removing the `Exception` suffix and converting the remaining name to uppercase snake case. For example, `InsufficientPointsException` produces `INSUFFICIENT_POINTS`.

Each custom exception class name must describe the failure situation, such as `UserNotFoundException` or `InsufficientPointsException`. Do not use vague names such as `CommonException`, `BusinessException`, or `CustomException`.

Controllers must not catch `ServiceException` or its subclasses; `GlobalExceptionHandler` converts them into the common API response.

`GlobalExceptionHandler` converts framework validation and malformed-request failures to `InvalidRequestException`, and converts unexpected failures to `InternalServerErrorException` before creating the response. Application code must not expose framework exception details or stack traces to clients.

Example error response:

```http
HTTP/1.1 409 Conflict
Content-Type: application/json
```

```json
{
  "httpStatus": 409,
  "data": {
    "code": "INSUFFICIENT_POINTS",
    "message": "The user does not have enough points to purchase this menu.",
    "errors": []
  }
}
```

Validation errors include one item for each invalid field:

```json
{
  "httpStatus": 400,
  "data": {
    "code": "INVALID_REQUEST",
    "message": "One or more request fields are invalid.",
    "errors": [
      {
        "field": "menuId",
        "reason": "must be a positive integer"
      }
    ]
  }
}
```

### Standard Exception Mapping

| Exception class | HTTP status | Response code | Meaning |
|-----------------|-------------|---------------|---------|
| `InvalidRequestException` | `400 Bad Request` | `INVALID_REQUEST` | The path, JSON syntax, or request fields are invalid |
| `UserNotFoundException` | `404 Not Found` | `USER_NOT_FOUND` | The requested user does not exist |
| `MenuNotFoundException` | `404 Not Found` | `MENU_NOT_FOUND` | The requested menu does not exist |
| `OrderAttemptNotFoundException` | `404 Not Found` | `ORDER_ATTEMPT_NOT_FOUND` | The order attempt does not exist |
| `InsufficientPointsException` | `409 Conflict` | `INSUFFICIENT_POINTS` | The user cannot pay the current menu price |
| `IdempotencyKeyReusedException` | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | An idempotency key was reused with a different request |
| `OrderAttemptExpiredException` | `410 Gone` | `ORDER_ATTEMPT_EXPIRED` | The order attempt expired before confirmation |
| `InternalServerErrorException` | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | An unexpected server error occurred |
| `ServiceUnavailableException` | `503 Service Unavailable` | `SERVICE_UNAVAILABLE` | A required dependency is temporarily unavailable |

## 8. Logging Convention

- Console log messages written by the application must be in Korean.
- Expected client errors may be logged without a stack trace.
- Unexpected errors must be logged with their stack trace and a Korean diagnostic message.
- Logs must not contain credentials, secrets, or sensitive request data.
