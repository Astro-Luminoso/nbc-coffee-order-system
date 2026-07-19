package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

/**
 * 완료 주문 데이터를 외부 수집 플랫폼으로 전달한다.
 */
public interface DataCollectionClient {

    /**
     * 완료된 주문의 수집 데이터를 외부 플랫폼으로 전달한다.
     *
     * @param orderId 주문 식별자
     * @param userId 사용자 식별자
     * @param menuId 메뉴 식별자
     * @param paymentAmount 결제 금액
     */
    void collect(long orderId, long userId, long menuId, long paymentAmount);
}
