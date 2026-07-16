package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service.PointChargeFacade;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.IdempotencyKeyReusedException;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.UserNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 포인트 충전 API의 HTTP 계약을 검증한다.
 */
@WebMvcTest(PointChargeController.class)
class PointChargeControllerIntegrationTest {

    @MockitoBean
    private PointChargeFacade pointChargeFacade;

    @Autowired
    private MockMvc mockMvc;

    /**
     * 정상 충전이 요구된 공통 응답 구조와 충전 후 잔액을 반환하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointReturnsChargedAmountAndUpdatedBalance() throws Exception {
        when(pointChargeFacade.charge(1L, "charge-1", 10_000L))
                .thenReturn(PointChargeResponse.of(1L, 10_000L, 15_000L));

        mockMvc.perform(chargeRequest(1L, "charge-1", 10_000L))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.chargedAmount").value(10_000))
                .andExpect(jsonPath("$.data.balance").value(15_000));
    }

    /**
     * 동일한 멱등성 키의 재시도가 같은 응답을 반환하고 파사드에 전달되는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointReplaysTheOriginalResponseForTheSameKey() throws Exception {
        PointChargeResponse originalResponse = PointChargeResponse.of(1L, 10_000L, 15_000L);
        when(pointChargeFacade.charge(1L, "charge-1", 10_000L)).thenReturn(originalResponse);

        mockMvc.perform(chargeRequest(1L, "charge-1", 10_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(15_000));
        mockMvc.perform(chargeRequest(1L, "charge-1", 10_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(15_000));

        verify(pointChargeFacade, times(2)).charge(1L, "charge-1", 10_000L);
    }

    /**
     * 다른 요청에 이미 사용된 멱등성 키가 충돌 응답으로 변환되는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointReturnsConflictWhenKeyIsReusedForAnotherRequest() throws Exception {
        when(pointChargeFacade.charge(1L, "charge-1", 20_000L))
                .thenThrow(new IdempotencyKeyReusedException("멱등성 키가 다른 요청에 이미 사용되었습니다."));

        mockMvc.perform(chargeRequest(1L, "charge-1", 20_000L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.httpStatus").value(409))
                .andExpect(jsonPath("$.data.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    /**
     * 존재하지 않는 사용자가 사용자 없음 응답으로 변환되는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(pointChargeFacade.charge(99L, "charge-1", 10_000L))
                .thenThrow(new UserNotFoundException(99L));

        mockMvc.perform(chargeRequest(99L, "charge-1", 10_000L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.data.code").value("USER_NOT_FOUND"));
    }

    /**
     * 잘못된 경로, 본문, 멱등성 헤더가 모두 잘못된 요청 응답이 되는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointRejectsInvalidPathBodyAndHeader() throws Exception {
        mockMvc.perform(chargeRequest(0L, "charge-1", 10_000L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(chargeRequest(1L, "charge-1", 0L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(chargeRequest(1L, "키", 10_000L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(chargeRequest(1L, "a".repeat(129), 10_000L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/users/1/point-charges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));

        verify(pointChargeFacade, never()).charge(anyLong(), anyString(), anyLong());
    }

    /**
     * 여러 충전 요청이 동시에 도착해도 각 HTTP 요청이 성공적으로 전달되는지 검증한다.
     *
     * @throws Exception 동시 HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointAcceptsConcurrentCharges() throws Exception {
        int requestCount = 10;
        AtomicLong balance = new AtomicLong();
        when(pointChargeFacade.charge(anyLong(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    long userId = invocation.getArgument(0);
                    long amount = invocation.getArgument(2);
                    return PointChargeResponse.of(userId, amount, balance.addAndGet(amount));
                });
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int index = 0; index < requestCount; index++) {
            int requestIndex = index;
            requests.add(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return mockMvc.perform(chargeRequest(1L, "charge-" + requestIndex, 1_000L))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });
        }

        List<Future<Integer>> results = new ArrayList<>();
        for (Callable<Integer> request : requests) {
            results.add(executor.submit(request));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();

        for (Future<Integer> result : results) {
            assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(balance.get()).isEqualTo(10_000L);
    }

    /**
     * 포인트 충전 HTTP 요청을 생성한다.
     *
     * @param userId 포인트를 충전할 사용자 식별자
     * @param idempotencyKey 충전 시도를 식별하는 멱등성 키
     * @param amount 충전 금액
     * @return 구성된 HTTP 요청 작성기
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder chargeRequest(
            long userId,
            String idempotencyKey,
            long amount
    ) {
        return post("/api/v1/users/{userId}/point-charges", userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}");
    }
}
