package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.PreparedPointCharge;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.InternalServerErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 예약된 포인트 충전을 하나의 트랜잭션으로 처리한다.
 */
@Service
public class PointChargeTransactionService {

    private final UserService userService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * 포인트 충전 처리에 필요한 도메인 서비스를 사용한다.
     *
     * @param userService 사용자 잔액을 충전하는 서비스
     * @param idempotencyService 멱등성 기록을 관리하는 서비스
     * @param objectMapper 완료 응답을 직렬화하고 역직렬화하는 객체
     */
    public PointChargeTransactionService(
            UserService userService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper
    ) {
        this.userService = userService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * 멱등성 행을 잠근 상태에서 포인트를 충전하거나 저장된 완료 결과를 재생한다.
     *
     * @param userId 포인트를 충전할 사용자 식별자
     * @param idempotencyKey 충전 시도를 식별하는 멱등성 키
     * @param amount 충전할 포인트 수
     * @return 충전 또는 재시도 결과
     */
    @Transactional
    public PointChargeResponse charge(long userId, String idempotencyKey, long amount) {
        PreparedPointCharge prepared = idempotencyService.preparePointCharge(
                idempotencyKey,
                userId,
                amount
        );
        if (prepared.completed()) {
            return readCompletedResponse(prepared.responseBody());
        }

        long balance = userService.charge(userId, amount);
        PointChargeResponse response = PointChargeResponse.of(userId, amount, balance);
        idempotencyService.completePointCharge(
                prepared,
                HttpStatus.OK.value(),
                writeCompletedResponse(response)
        );
        return response;
    }

    /**
     * 저장된 공통 응답 본문에서 포인트 충전 결과를 복원한다.
     *
     * @param responseBody 저장된 공통 응답 JSON
     * @return 복원된 포인트 충전 결과
     */
    private PointChargeResponse readCompletedResponse(String responseBody) {
        try {
            CommonApiResponse<PointChargeResponse> response = objectMapper.readValue(
                    responseBody,
                    new TypeReference<CommonApiResponse<PointChargeResponse>>() {
                    }
            );
            return response.data();
        } catch (JacksonException exception) {
            throw new InternalServerErrorException("저장된 포인트 충전 결과를 처리할 수 없습니다.");
        }
    }

    /**
     * 포인트 충전 결과를 멱등성 기록에 저장할 공통 응답 JSON으로 변환한다.
     *
     * @param response 저장할 포인트 충전 결과
     * @return 공통 응답 JSON
     */
    private String writeCompletedResponse(PointChargeResponse response) {
        try {
            return objectMapper.writeValueAsString(
                    CommonApiResponse.of(HttpStatus.OK.value(), response)
            );
        } catch (JacksonException exception) {
            throw new InternalServerErrorException("포인트 충전 결과를 저장할 수 없습니다.");
        }
    }
}
