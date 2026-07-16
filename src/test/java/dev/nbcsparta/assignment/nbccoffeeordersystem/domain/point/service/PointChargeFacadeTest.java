package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 포인트 충전 파사드의 예약과 트랜잭션 처리 위임을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PointChargeFacadeTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private PointChargeTransactionService pointChargeTransactionService;

    @InjectMocks
    private PointChargeFacade pointChargeFacade;

    /**
     * 새 멱등성 키를 예약한 뒤 포인트 충전 트랜잭션을 위임하는지 검증한다.
     */
    @Test
    void chargeReservesKeyBeforeDelegatingToTheTransactionService() {
        PointChargeResponse expected = PointChargeResponse.of(1L, 1_000L, 6_000L);
        when(pointChargeTransactionService.charge(1L, "charge-1", 1_000L)).thenReturn(expected);

        PointChargeResponse response = pointChargeFacade.charge(1L, "charge-1", 1_000L);

        assertThat(response).isEqualTo(expected);
        verify(idempotencyService).reservePointCharge("charge-1", 1L, 1_000L);
        verify(pointChargeTransactionService).charge(1L, "charge-1", 1_000L);
    }

    /**
     * 동시 예약 충돌이 발생해도 기존 레코드의 트랜잭션 처리를 계속 위임하는지 검증한다.
     */
    @Test
    void chargeContinuesWhenAnotherRequestAlreadyReservedTheSameKey() {
        PointChargeResponse expected = PointChargeResponse.of(1L, 1_000L, 6_000L);
        doThrow(duplicateKeyException())
                .when(idempotencyService)
                .reservePointCharge("charge-1", 1L, 1_000L);
        when(idempotencyService.existsPointCharge("charge-1")).thenReturn(true);
        when(pointChargeTransactionService.charge(1L, "charge-1", 1_000L)).thenReturn(expected);

        PointChargeResponse response = pointChargeFacade.charge(1L, "charge-1", 1_000L);

        assertThat(response).isEqualTo(expected);
        verify(idempotencyService).existsPointCharge("charge-1");
        verify(pointChargeTransactionService).charge(1L, "charge-1", 1_000L);
    }

    /**
     * 중복 키가 아닌 무결성 제약 위반은 숨기지 않고 전파하는지 검증한다.
     */
    @Test
    void chargePropagatesNonDuplicateIntegrityViolation() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "다른 제약 위반",
                new SQLException("검사 제약 위반", "23514")
        );
        doThrow(exception)
                .when(idempotencyService)
                .reservePointCharge("charge-1", 1L, 1_000L);

        assertThatThrownBy(() -> pointChargeFacade.charge(1L, "charge-1", 1_000L))
                .isSameAs(exception);

        verify(idempotencyService, never()).existsPointCharge("charge-1");
        verify(pointChargeTransactionService, never()).charge(1L, "charge-1", 1_000L);
    }

    /**
     * 연결 실패와 같은 DB 접근 오류는 중복 키 경합으로 취급하지 않는지 검증한다.
     */
    @Test
    void chargePropagatesDatabaseAccessFailure() {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("데이터베이스 연결 실패");
        doThrow(exception)
                .when(idempotencyService)
                .reservePointCharge("charge-1", 1L, 1_000L);

        assertThatThrownBy(() -> pointChargeFacade.charge(1L, "charge-1", 1_000L))
                .isSameAs(exception);

        verify(idempotencyService, never()).existsPointCharge("charge-1");
        verify(pointChargeTransactionService, never()).charge(1L, "charge-1", 1_000L);
    }

    /**
     * H2의 복합 기본 키 중복 오류를 표현하는 예외를 생성한다.
     *
     * @return 중복 키 무결성 제약 위반 예외
     */
    private DataIntegrityViolationException duplicateKeyException() {
        return new DataIntegrityViolationException(
                "멱등성 키 충돌",
                new SQLException("기본 키 중복", "23505")
        );
    }
}
