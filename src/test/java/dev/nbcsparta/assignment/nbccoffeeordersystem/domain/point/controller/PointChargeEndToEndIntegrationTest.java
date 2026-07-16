package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecord;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 HTTP 계층과 영속 계층을 함께 사용하는 포인트 충전 종단간 통합 테스트이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PointChargeEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    /**
     * 동일 키의 재시도가 잔액을 한 번만 변경하고 최초 응답을 재생하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointReplaysTheOriginalResponseWithoutChargingTwice() throws Exception {
        User user = userRepository.saveAndFlush(new User(5_000L));

        mockMvc.perform(chargeRequest(user.getId(), "end-to-end-replay", 1_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.balance").value(6_000));
        mockMvc.perform(chargeRequest(user.getId(), "end-to-end-replay", 1_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.balance").value(6_000));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance()).isEqualTo(6_000L);
        assertThat(idempotencyRecordRepository.findAll())
                .singleElement()
                .satisfies(record -> assertThat(record.isCompleted()).isTrue());
    }

    /**
     * 같은 키를 다른 충전 요청에 재사용하면 잔액 변경 없이 충돌을 반환하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void chargePointRejectsDifferentRequestForAnExistingIdempotencyKey() throws Exception {
        User user = userRepository.saveAndFlush(new User(5_000L));

        mockMvc.perform(chargeRequest(user.getId(), "end-to-end-conflict", 1_000L))
                .andExpect(status().isOk());
        mockMvc.perform(chargeRequest(user.getId(), "end-to-end-conflict", 2_000L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance()).isEqualTo(6_000L);
        assertThat(idempotencyRecordRepository.findAll()).hasSize(1);
    }

    /**
     * 각 테스트가 생성한 멱등성 기록과 사용자를 삭제한다.
     */
    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    /**
     * 포인트 충전 HTTP 요청을 생성한다.
     *
     * @param userId 포인트를 충전할 사용자 식별자
     * @param idempotencyKey 충전 시도를 식별하는 멱등성 키
     * @param amount 충전 금액
     * @return 구성된 HTTP 요청 작성기
     */
    private MockHttpServletRequestBuilder chargeRequest(long userId, String idempotencyKey, long amount) {
        return post("/api/v1/users/{userId}/point-charges", userId)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}");
    }
}
