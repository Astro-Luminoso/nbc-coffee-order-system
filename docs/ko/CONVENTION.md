# 커피 주문 시스템 컨벤션

> 원문: [English Conventions](../CONVENTION.md)

## 1. HTTP 컨벤션

- 모든 공개 API 엔드포인트는 `/api/v1` 기본 경로를 사용합니다.
- 요청·응답 본문은 `application/json`과 camel-case 필드를 사용합니다.
- 도메인 식별자는 양의 64비트 정수입니다.
- 가격, 충전 금액, 잔액, 결제 금액은 정수 포인트입니다. 1원은 1포인트입니다.
- 날짜·시간 값은 UTC 오프셋을 포함한 ISO 8601 형식을 사용합니다.
- 인기 메뉴의 업무 시간대는 `Asia/Seoul`이며, Redis 일별 인기 메뉴 키도 이 시간대로 날짜를
  계산합니다.
- 인증과 인가는 현재 API 범위 밖입니다.

## 2. 멱등성 컨벤션

포인트 충전과 주문 결제 요청은 클라이언트가 생성한 `Idempotency-Key` 헤더가 필요합니다.

- 키는 1~128자의 공백이 아닌 ASCII 문자열이며 UUID 사용을 권장합니다.
- 키의 범위는 작업 종류와 키의 조합입니다. `POINT_CHARGE`와 `ORDER_PAYMENT`는 서로 다른
  작업입니다.
- 애플리케이션은 공유 영속 저장소에 키와 정규 요청 해시를 함께 저장합니다.
- 완료된 요청과 같은 요청은 다른 부작용 없이 최초 HTTP 상태와 응답 본문을 반환합니다.
- 같은 키를 다른 정규 요청에 사용하면 `409 Conflict`와 `IDEMPOTENCY_KEY_REUSED`를
  반환합니다.
- 변경 작업과 완료된 멱등성 결과는 같은 DB 트랜잭션에서 커밋됩니다.
- 안전한 재시도 기간 중 결과가 삭제되지 않도록 레코드 만료 정책을 정의합니다.

멱등성은 하나의 논리적 요청이 중복 실행되는 것을 막습니다. 서로 다른 동시 요청으로부터
잔액을 보호하는 원자적 DB 갱신을 대체하지는 않습니다.

## 3. 공통 API 응답

모든 엔드포인트는 숫자 HTTP 상태와 엔드포인트별 데이터를 포함하는
`CommonApiResponse<T>` 본문을 반환합니다.

```json
{
  "httpStatus": 200,
  "data": {}
}
```

숫자 `httpStatus`는 HTTP 응답 상태 줄과 같아야 합니다. 컬렉션은 `null`이 아닌 빈 배열로
표현합니다.

## 4. 요청·응답 DTO

- 요청 DTO는 Java record이며 Jakarta Validation 제약을 사용합니다.
- 컨트롤러는 요청 DTO 매개변수에 `@Valid`를 적용합니다.
- 응답 DTO는 Java record이며 JPA 엔티티를 직접 노출하지 않습니다.
- 응답 변환은 컨트롤러가 아닌 DTO 팩터리 메서드에서 수행합니다.

## 5. 오류 처리

애플리케이션 실패는 상황별 예외로 표현하고 `GlobalExceptionHandler`가 처리합니다. 클라이언트는
안정적인 오류 코드, 안전한 메시지, 검증 실패 시 필드 오류를 받습니다.

```json
{
  "httpStatus": 409,
  "data": {
    "code": "INSUFFICIENT_POINTS",
    "message": "사용자에게 이 메뉴를 구매할 충분한 포인트가 없습니다.",
    "errors": []
  }
}
```

| 예외 클래스 | HTTP 상태 | 응답 코드 |
|---|---|---|
| `InvalidRequestException` | `400 Bad Request` | `INVALID_REQUEST` |
| `UserNotFoundException` | `404 Not Found` | `USER_NOT_FOUND` |
| `MenuNotFoundException` | `404 Not Found` | `MENU_NOT_FOUND` |
| `InsufficientPointsException` | `409 Conflict` | `INSUFFICIENT_POINTS` |
| `IdempotencyKeyReusedException` | `409 Conflict` | `IDEMPOTENCY_KEY_REUSED` |
| `InternalServerErrorException` | `500 Internal Server Error` | `INTERNAL_SERVER_ERROR` |

## 6. 로그와 코드 주석

- 콘솔 로그 메시지는 한국어로 작성합니다.
- 예기치 않은 실패에는 한국어 진단 메시지와 스택 트레이스를 포함합니다.
- 로그에 자격 증명, 비밀 정보, 민감한 요청 데이터를 포함하지 않습니다.
- Java 문서와 코드 주석은 한국어로 작성합니다.
