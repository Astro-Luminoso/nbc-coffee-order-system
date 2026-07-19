# Coffee Order System Conventions

## 1. HTTP Conventions

- All public API endpoints use the `/api/v1` base path.
- Request and response bodies use `application/json` and camel-case fields.
- Domain identifiers are positive 64-bit integers.
- Prices, charges, balances, and payment amounts are integer points. One Korean
  won equals one point.
- Date-time values use ISO 8601 with a UTC offset.
- The popularity business timezone is `Asia/Seoul`; daily Redis popularity keys
  use dates resolved in this timezone.
- Authentication and authorization are outside the current API scope.

## 2. Idempotency Convention

Point-charge and order-payment requests require a client-generated
`Idempotency-Key` header.

- The key is a non-blank ASCII string between 1 and 128 characters. UUIDs are
  recommended.
- The key scope is the operation plus the key. `POINT_CHARGE` and
  `ORDER_PAYMENT` are separate operations.
- The application stores the canonical request hash with the key in shared
  durable storage.
- A matching completed request returns its original HTTP status and response
  body without applying another side effect.
- Reusing a key with a different canonical request returns `409 Conflict` with
  `IDEMPOTENCY_KEY_REUSED`.
- The mutation and its completed idempotency result commit in the same
  database transaction.
- Records have a defined expiration policy so cleanup cannot remove a result
  while clients may still safely retry it.

Idempotency prevents duplicate execution of one logical request. It does not
replace the atomic database updates that protect a balance from different
concurrent requests.

## 3. Common API Response

Every endpoint returns a `CommonApiResponse<T>` body containing the numeric HTTP
status and endpoint-specific data.

```json
{
  "httpStatus": 200,
  "data": {}
}
```

The numeric `httpStatus` must match the HTTP response status line. Collections
must be represented as empty arrays rather than `null`.

## 4. Request and Response DTOs

- Request DTOs are Java records and use Jakarta Validation constraints.
- Controllers apply `@Valid` to request DTO parameters.
- Response DTOs are Java records and do not expose JPA entities.
- Response mapping belongs in DTO factory methods rather than controllers.

## 5. Error Handling

Application failures use situation-specific exceptions handled by
`GlobalExceptionHandler`. Clients receive a stable error code, safe message,
and field errors when validation fails.

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

| Exception class | HTTP status | Response code |
|---|---|---|
| `InvalidRequestException` | `400 Bad Request` | `INVALID_REQUEST` |
| `UserNotFoundException` | `404 Not Found` | `USER_NOT_FOUND` |
| `MenuNotFoundException` | `404 Not Found` | `MENU_NOT_FOUND` |
| `InsufficientPointsException` | `409 Conflict` | `INSUFFICIENT_POINTS` |
| `IdempotencyKeyReusedException` | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| `InternalServerErrorException` | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 6. Logging and Code Comments

- Console log messages must be written in Korean.
- Unexpected failures must include a Korean diagnostic message and stack trace.
- Logs must not contain credentials, secrets, or sensitive request data.
- Java documentation and code comments must be written in Korean.
