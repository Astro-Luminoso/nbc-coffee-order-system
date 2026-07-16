package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        doThrow(new DataIntegrityViolationException("멱등성 키 충돌"))
                .when(idempotencyService)
                .reservePointCharge("charge-1", 1L, 1_000L);
        when(pointChargeTransactionService.charge(1L, "charge-1", 1_000L)).thenReturn(expected);

        PointChargeResponse response = pointChargeFacade.charge(1L, "charge-1", 1_000L);

        assertThat(response).isEqualTo(expected);
        verify(pointChargeTransactionService).charge(1L, "charge-1", 1_000L);
    }
}
