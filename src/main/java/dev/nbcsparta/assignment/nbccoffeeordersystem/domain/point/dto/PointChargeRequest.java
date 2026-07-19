package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.point.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 포인트 충전 요청을 표현한다.
 *
 * @param amount 충전할 금액과 동일한 포인트 수
 */
public record PointChargeRequest(
        @NotNull(message = "충전 금액은 필수입니다.")
        @Positive(message = "충전 금액은 양수여야 합니다.")
        Long amount
) {
}
