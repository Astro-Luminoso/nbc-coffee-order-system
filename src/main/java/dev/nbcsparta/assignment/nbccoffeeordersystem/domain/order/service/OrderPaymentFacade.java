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

    public OrderPaymentFacade(IdempotencyService idempotencyService, OrderPaymentService orderPaymentService) {
        this.idempotencyService = idempotencyService;
        this.orderPaymentService = orderPaymentService;
    }

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
