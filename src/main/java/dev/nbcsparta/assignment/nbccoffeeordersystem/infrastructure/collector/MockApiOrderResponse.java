package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

/**
 * MockAPI.io가 반환한 완료 주문 데이터다.
 *
 * @param id MockAPI.io가 생성한 식별자
 * @param orderId 주문 식별자
 * @param userId 사용자 식별자
 * @param menuId 메뉴 식별자
 * @param paymentAmount 결제 금액
 */
public record MockApiOrderResponse(
        String id,
        long orderId,
        long userId,
        long menuId,
        long paymentAmount
) {

    /**
     * 외부 응답이 유효한 식별자를 가지며 요청 주문과 같은지 확인한다.
     *
     * @param request 비교할 주문 요청
     * @return 유효한 식별자와 동일한 주문 데이터를 가지면 {@code true}
     */
    public boolean matches(MockApiOrderRequest request) {
        return id != null
                && !id.isBlank()
                && orderId == request.orderId()
                && userId == request.userId()
                && menuId == request.menuId()
                && paymentAmount == request.paymentAmount();
    }
}
