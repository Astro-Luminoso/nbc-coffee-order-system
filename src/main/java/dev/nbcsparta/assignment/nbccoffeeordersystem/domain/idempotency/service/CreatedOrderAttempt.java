package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service;

import java.time.Instant;
import java.util.Objects;

/**
 * 새로 저장된 대기 상태 주문 시도의 식별 정보이다.
 *
 * @param orderAttemptId 서버가 발급한 주문 시도 식별자
 * @param expiresAt 주문 시도를 확인할 수 있는 마지막 시각
 */
public record CreatedOrderAttempt(
        String orderAttemptId,
        Instant expiresAt
) {

    /**
     * 주문 시도 식별 정보의 필수 값을 검증한다.
     *
     * @param orderAttemptId 서버가 발급한 주문 시도 식별자
     * @param expiresAt 주문 시도를 확인할 수 있는 마지막 시각
     */
    public CreatedOrderAttempt {
        Objects.requireNonNull(orderAttemptId);
        Objects.requireNonNull(expiresAt);
    }
}
