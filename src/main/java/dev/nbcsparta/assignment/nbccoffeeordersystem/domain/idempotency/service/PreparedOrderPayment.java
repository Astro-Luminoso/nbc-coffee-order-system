package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service;

/**
 * 주문 결제 멱등성 레코드 잠금 결과를 표현한다.
 */
public record PreparedOrderPayment(
        String idempotencyKey,
        boolean completed,
        Integer httpStatus,
        String responseBody
) {
    public static PreparedOrderPayment pending(String idempotencyKey) {
        return new PreparedOrderPayment(idempotencyKey, false, null, null);
    }

    public static PreparedOrderPayment completed(String idempotencyKey, int httpStatus, String responseBody) {
        return new PreparedOrderPayment(idempotencyKey, true, httpStatus, responseBody);
    }
}
