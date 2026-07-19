package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 단일 메뉴 주문과 결제 요청을 표현한다.
 */
public record CreateOrderRequest(
        @NotNull(message = "사용자 식별자는 필수입니다.")
        @Positive(message = "사용자 식별자는 양수여야 합니다.")
        Long userId,
        @NotNull(message = "메뉴 식별자는 필수입니다.")
        @Positive(message = "메뉴 식별자는 양수여야 합니다.")
        Long menuId
) {
}
