package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.CreatedOrderAttempt;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 저장된 대기 주문 시도 정보를 응답으로 표현한다.
 *
 * @param orderAttemptId 서버가 생성한 주문 시도 식별자
 * @param status 주문 시도 상태
 * @param expiresAt 주문 시도 만료 시각
 */
public record CreateOrderAttemptResponse(
        String orderAttemptId,
        String status,
        OffsetDateTime expiresAt
) {

    private static final String PENDING_STATUS = "PENDING";
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Seoul");

    /**
     * 영속된 주문 시도 생성 결과를 API 응답으로 변환한다.
     *
     * @param createdOrderAttempt 영속된 주문 시도 생성 결과
     * @return 주문 시도 생성 응답
     */
    public static CreateOrderAttemptResponse from(CreatedOrderAttempt createdOrderAttempt) {
        return new CreateOrderAttemptResponse(
                createdOrderAttempt.orderAttemptId(),
                PENDING_STATUS,
                createdOrderAttempt.expiresAt().atZone(BUSINESS_ZONE_ID).toOffsetDateTime()
        );
    }
}
