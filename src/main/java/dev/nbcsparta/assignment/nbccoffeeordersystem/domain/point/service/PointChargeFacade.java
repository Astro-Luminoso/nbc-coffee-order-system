package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 포인트 충전 멱등성 예약과 트랜잭션 처리를 조정한다.
 */
@Service
public class PointChargeFacade {

    private final IdempotencyService idempotencyService;
    private final PointChargeTransactionService pointChargeTransactionService;

    /**
     * 포인트 충전 처리에 필요한 서비스를 사용한다.
     *
     * @param idempotencyService 멱등성 기록을 관리하는 서비스
     * @param pointChargeTransactionService 포인트 충전을 트랜잭션으로 처리하는 서비스
     */
    public PointChargeFacade(
            IdempotencyService idempotencyService,
            PointChargeTransactionService pointChargeTransactionService
    ) {
        this.idempotencyService = idempotencyService;
        this.pointChargeTransactionService = pointChargeTransactionService;
    }

    /**
     * 포인트 충전 레코드를 예약한 뒤 트랜잭션 처리를 위임한다.
     *
     * @param userId 포인트를 충전할 사용자 식별자
     * @param idempotencyKey 충전 시도를 식별하는 멱등성 키
     * @param amount 충전할 포인트 수
     * @return 충전 또는 재시도 결과
     */
    public PointChargeResponse charge(long userId, String idempotencyKey, long amount) {
        try {
            idempotencyService.reservePointCharge(idempotencyKey, userId, amount);
        } catch (DataAccessException ignored) {
            // 같은 키를 먼저 예약한 요청이 있으므로 잠금 처리 단계에서 결과를 재사용한다.
        }
        return pointChargeTransactionService.charge(userId, idempotencyKey, amount);
    }
}
