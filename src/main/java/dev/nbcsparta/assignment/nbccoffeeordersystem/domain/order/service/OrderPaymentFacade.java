package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto.OrderPaymentResponse;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 주문 결제 멱등성 예약과 트랜잭션 처리를 조정한다.
 */
@Service
public class OrderPaymentFacade {

    private static final String H2_DUPLICATE_KEY_SQL_STATE = "23505";
    private static final String MYSQL_INTEGRITY_CONSTRAINT_SQL_STATE = "23000";
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;
    private final IdempotencyService idempotencyService;
    private final OrderPaymentService orderPaymentService;

    /**
     * 주문 결제의 멱등성 예약과 트랜잭션 처리를 조합한다.
     *
     * @param idempotencyService 멱등성 상태 서비스
     * @param orderPaymentService 주문 결제 트랜잭션 서비스
     */
    public OrderPaymentFacade(IdempotencyService idempotencyService, OrderPaymentService orderPaymentService) {
        this.idempotencyService = idempotencyService;
        this.orderPaymentService = orderPaymentService;
    }

    /**
     * 멱등성 키를 예약한 뒤 주문 결제를 실행하거나 완료 결과를 재생한다.
     *
     * @param idempotencyKey 주문 시도 식별 키
     * @param userId 결제할 사용자 식별자
     * @param menuId 주문할 메뉴 식별자
     * @return 주문 결제 결과
     */
    public OrderPaymentResponse pay(String idempotencyKey, long userId, long menuId) {
        try {
            idempotencyService.reserveOrderPayment(idempotencyKey, userId, menuId);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateKeyViolation(exception)) {
                throw exception;
            }
        }
        return orderPaymentService.pay(idempotencyKey, userId, menuId);
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return H2_DUPLICATE_KEY_SQL_STATE.equals(sqlException.getSQLState())
                        || (MYSQL_INTEGRITY_CONSTRAINT_SQL_STATE.equals(sqlException.getSQLState())
                        && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE);
            }
            cause = cause.getCause();
        }
        return false;
    }
}
