package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 포인트 충전 멱등성 예약과 트랜잭션 처리를 조정한다.
 */
@Service
public class PointChargeFacade {

    private static final String H2_DUPLICATE_KEY_SQL_STATE = "23505";
    private static final String MYSQL_INTEGRITY_CONSTRAINT_SQL_STATE = "23000";
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

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
        } catch (DataIntegrityViolationException exception) {
            if (!isCommittedDuplicateReservation(exception, idempotencyKey)) {
                throw exception;
            }
        }
        return pointChargeTransactionService.charge(userId, idempotencyKey, amount);
    }

    /**
     * H2 또는 MySQL에서 동일 멱등성 키를 먼저 저장한 요청이 있는지 확인한다.
     *
     * <p>무결성 제약 위반만으로는 다른 스키마 오류를 구분할 수 없으므로, DB 방언별
     * 중복 키 식별자와 예약 레코드의 실제 존재 여부를 모두 확인한다.</p>
     *
     * @param exception 예약 중 발생한 무결성 제약 위반
     * @param idempotencyKey 확인할 멱등성 키
     * @return 이미 커밋된 동일 키 예약이 확인되면 {@code true}
     */
    private boolean isCommittedDuplicateReservation(
            DataIntegrityViolationException exception,
            String idempotencyKey
    ) {
        return isDuplicateKeyViolation(exception)
                && idempotencyService.existsPointCharge(idempotencyKey);
    }

    /**
     * 예외 원인 사슬에서 H2 또는 MySQL의 중복 키 오류를 식별한다.
     *
     * @param exception 확인할 무결성 제약 위반 예외
     * @return 중복 키 오류이면 {@code true}
     */
    private boolean isDuplicateKeyViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                return H2_DUPLICATE_KEY_SQL_STATE.equals(sqlState)
                        || (MYSQL_INTEGRITY_CONSTRAINT_SQL_STATE.equals(sqlState)
                        && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE);
            }
            cause = cause.getCause();
        }
        return false;
    }
}
