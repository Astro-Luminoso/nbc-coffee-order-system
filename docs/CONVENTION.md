# Coffee Order System Conventions

## 1. HTTP Conventions

- All public API endpoints use the `/api/v1` base path.
- Request and response bodies use `application/json`.
- Field names use `camelCase`.
- Identifiers are positive 64-bit integers.
- Prices, charges, balances, and payment amounts are integer points. One Korean won equals one point.
- Date-time values use ISO 8601 with a UTC offset, such as `2026-07-13T14:30:00+09:00`.
- Unknown JSON request fields are ignored for forward compatibility.
- Authentication and authorization are outside the current API scope.

## 2. Common API Response

Every endpoint returns a `CommonApiResponse<T>` body containing the HTTP status and an endpoint-specific response DTO in `data`.

```java
public record CommonApiResponse<T>(
        int httpStatus,
        T data
) {
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

## 3. Response DTO Conventions

- Every response DTO must be declared as a Java `record`.
- Response DTOs must contain API-facing values only and must not expose JPA entities.
- A collection response must return an empty array instead of `null` when no items exist.
- A response field that is required by the API contract must not be `null`.
- The common response wrapper and nested error responses are also records.

Example endpoint response DTO:

```java
public record MenuListResponse(
        List<MenuResponse> menus
) {
    public record MenuResponse(
            Long id,
            String name,
            Long price
    ) {
    }
}
```

## 4. Exception Handling

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
| `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` | An unexpected server error occurred |
| `503 Service Unavailable` | `SERVICE_UNAVAILABLE` | A required dependency is temporarily unavailable |

## 5. Logging Convention

- Console log messages written by the application must be in Korean.
- Expected client errors may be logged without a stack trace.
- Unexpected errors must be logged with their stack trace and a Korean diagnostic message.
- Logs must not contain credentials, secrets, or sensitive request data.
