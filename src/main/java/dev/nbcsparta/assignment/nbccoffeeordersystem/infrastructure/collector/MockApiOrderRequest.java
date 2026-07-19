package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

/**
 * MockAPI.io에 전달할 완료 주문 데이터다.
 *
 * @param orderId 주문 식별자
 * @param userId 사용자 식별자
 * @param menuId 메뉴 식별자
 * @param paymentAmount 결제 금액
 */
public record MockApiOrderRequest(
        long orderId,
        long userId,
        long menuId,
        long paymentAmount
) {
}
