package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.controller;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeRequest;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto.PointChargeResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.service.PointChargeFacade;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 충전 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/v1/users")
public class PointChargeController {

    private final PointChargeFacade pointChargeFacade;

    /**
     * 포인트 충전 컨트롤러를 생성한다.
     *
     * @param pointChargeFacade 포인트 충전 유스케이스 파사드
     */
    public PointChargeController(PointChargeFacade pointChargeFacade) {
        this.pointChargeFacade = pointChargeFacade;
    }

    /**
     * 사용자의 포인트를 충전하고 충전 후 잔액을 반환한다.
     *
     * @param userId 포인트를 충전할 사용자 식별자
     * @param idempotencyKey 충전 시도를 식별하는 멱등성 키
     * @param request 충전 금액 요청
     * @return 포인트 충전 결과 공통 응답
     */
    @PostMapping("/{userId}/point-charges")
    public ResponseEntity<CommonApiResponse<PointChargeResponse>> chargePoint(
            @PathVariable @Positive(message = "사용자 식별자는 양수여야 합니다.") long userId,
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "멱등성 키는 필수입니다.")
            @Size(max = 128, message = "멱등성 키는 128자 이하여야 합니다.")
            @Pattern(regexp = "^[\\x20-\\x7E]+$", message = "멱등성 키는 ASCII 문자열이어야 합니다.")
            String idempotencyKey,
            @Valid @RequestBody PointChargeRequest request
    ) {
        PointChargeResponse response = pointChargeFacade.charge(
                userId,
                idempotencyKey,
                request.amount()
        );

        return ResponseEntity.ok(CommonApiResponse.of(HttpStatus.OK.value(), response));
    }
}
