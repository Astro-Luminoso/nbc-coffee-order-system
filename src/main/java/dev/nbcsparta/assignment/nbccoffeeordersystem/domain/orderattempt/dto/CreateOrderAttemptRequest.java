package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 주문 시도 생성을 요청하는 입력값을 표현한다.
 *
 * @param userId 주문할 사용자 식별자
 * @param menuId 주문할 메뉴 식별자
 */
public record CreateOrderAttemptRequest(
        @NotNull(message = "사용자 식별자는 필수입니다.")
        @Positive(message = "사용자 식별자는 양수여야 합니다.")
        Long userId,
        @NotNull(message = "메뉴 식별자는 필수입니다.")
        @Positive(message = "메뉴 식별자는 양수여야 합니다.")
        Long menuId
) {
}
