# Coffee Order System Conventions

## 1. HTTP Conventions

- All public API endpoints use the `/api/v1` base path.
- Request and response bodies use `application/json`.
- Field names use `camelCase`.
- Identifiers are positive 64-bit integers.
- Prices, charges, balances, and payment amounts are integer points. One Korean won equals one point.
- Date-time values use ISO 8601 with a UTC offset, such as `2026-07-13T14:30:00+09:00`.
- The business timezone is `Asia/Seoul`. Calendar-date windows and Redis daily keys must use dates resolved in this timezone.
- Unknown JSON request fields are ignored for forward compatibility.
- Authentication and authorization are outside the current API scope.

## 2. Idempotency Convention

- Every API that charges points or creates an order requires an `Idempotency-Key` request header.
- The header value must be a non-blank ASCII string between 1 and 128 characters. A UUID is recommended.
- The idempotency scope consists of the HTTP method, normalized endpoint path, and key. Path parameters and the request body form the request fingerprint.
- The idempotency result must be stored in shared durable storage so that retries remain safe across application instances and process restarts.
- A successful mutation and its idempotency result must commit in the same database transaction.
- The original successful HTTP status and response body must be retained for at least 24 hours.
- Repeating the same request in that period with the same key returns the original status and body without repeating any side effect.
- Concurrent requests with the same key and fingerprint must execute the business operation only once. Other callers receive the original result after it is available.
- Reusing the same key in the same scope with a different request fingerprint returns `409 Conflict` with `IDEMPOTENCY_KEY_REUSED`.
- A transaction that rolls back does not persist a successful idempotency result, so the client may retry it with the same key.

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

## 4. Response DTO Conventions

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

## 5. Java Code Conventions

- Lombok `@Getter`, `@Setter`, and `@Builder` annotations are prohibited.
- Classes must declare explicit methods only for behavior and access that the application actually requires.
- Every class, record, interface, enum, and method must have Javadoc written in Korean.
- All line comments (`//`) and block comments (`/* ... */`) written by the project must be in Korean.
- Javadoc tags such as `@param`, `@return`, and `@throws` remain in their standard form, but their descriptions must be in Korean.

## 6. Exception Handling

Business and application failures are represented by `CommonException`. The exception contains a stable error code and the HTTP status returned to the client.
Controllers must not catch `CommonException`; `GlobalExceptionHandler` converts it into the common API response.

Validation, malformed request, and unexpected exceptions are also handled centrally by `GlobalExceptionHandler`.
Unexpected exception details and stack traces must not be returned to clients.

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

### Common Errors

| HTTP status | Code | Meaning |
|-------------|------|---------|
| `400 Bad Request` | `INVALID_REQUEST` | The path, JSON syntax, or request fields are invalid |
| `404 Not Found` | `USER_NOT_FOUND` | The requested user does not exist |
| `404 Not Found` | `MENU_NOT_FOUND` | The requested menu does not exist |
| `409 Conflict` | `INSUFFICIENT_POINTS` | The user cannot pay the current menu price |
| `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` | An idempotency key was reused with a different request |
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | An unexpected server error occurred |
| `503 Service Unavailable` | `SERVICE_UNAVAILABLE` | A required dependency is temporarily unavailable |

## 7. Logging Convention

- Console log messages written by the application must be in Korean.
- Expected client errors may be logged without a stack trace.
- Unexpected errors must be logged with their stack trace and a Korean diagnostic message.
- Logs must not contain credentials, secrets, or sensitive request data.
