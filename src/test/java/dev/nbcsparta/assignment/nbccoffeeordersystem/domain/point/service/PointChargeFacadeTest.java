package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.PreparedPointCharge;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * 포인트 충전 파사드의 도메인 조정 책임을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PointChargeFacadeTest {

    @Mock
    private UserService userService;

    @Mock
    private IdempotencyService idempotencyService;

    private PointChargeFacade pointChargeFacade;

    /**
     * 각 테스트 전에 실제 JSON 변환기를 사용하는 파사드를 구성한다.
     */
    @BeforeEach
    void setUp() {
        pointChargeFacade = new PointChargeFacade(
                userService,
                idempotencyService,
                new ObjectMapper()
        );
    }

    /**
     * 신규 충전은 잔액을 변경하고 공통 응답 전체를 완료 기록으로 저장하는지 검증한다.
     *
     * @throws Exception JSON 검증에 실패한 경우
     */
    @Test
    void chargeCompletesIdempotencyRecordWithTheOriginalCommonResponse() throws Exception {
        PreparedPointCharge prepared = new PreparedPointCharge("charge-1", false, 0, null);
        when(idempotencyService.preparePointCharge("charge-1", 1L, 10_000L)).thenReturn(prepared);
        when(userService.charge(1L, 10_000L)).thenReturn(15_000L);

        PointChargeResponse response = pointChargeFacade.charge(1L, "charge-1", 10_000L);

        assertThat(response).isEqualTo(PointChargeResponse.of(1L, 10_000L, 15_000L));
        ArgumentCaptor<String> responseBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(idempotencyService).completePointCharge(
                eq(prepared),
                eq(200),
                responseBodyCaptor.capture()
        );
        CommonApiResponse<?> storedResponse = new ObjectMapper().readValue(
                responseBodyCaptor.getValue(),
                CommonApiResponse.class
        );
        assertThat(storedResponse.httpStatus()).isEqualTo(200);
        assertThat(storedResponse.data()).isEqualTo(java.util.Map.of(
                "userId", 1,
                "chargedAmount", 10_000,
                "balance", 15_000
        ));
    }

    /**
     * 완료된 동일 요청은 저장된 응답만 반환하고 잔액을 다시 변경하지 않는지 검증한다.
     *
     * @throws Exception JSON 구성에 실패한 경우
     */
    @Test
    void chargeReplaysCompletedResponseWithoutChargingAgain() throws Exception {
        String storedResponse = new ObjectMapper().writeValueAsString(
                CommonApiResponse.of(200, PointChargeResponse.of(1L, 10_000L, 15_000L))
        );
        PreparedPointCharge prepared = new PreparedPointCharge(
                "charge-1",
                true,
                200,
                storedResponse
        );
        when(idempotencyService.preparePointCharge("charge-1", 1L, 10_000L)).thenReturn(prepared);

        PointChargeResponse response = pointChargeFacade.charge(1L, "charge-1", 10_000L);

        assertThat(response).isEqualTo(PointChargeResponse.of(1L, 10_000L, 15_000L));
        verify(userService, never()).charge(anyLong(), anyLong());
        verify(idempotencyService, never()).completePointCharge(any(), eq(200), any());
    }
}
