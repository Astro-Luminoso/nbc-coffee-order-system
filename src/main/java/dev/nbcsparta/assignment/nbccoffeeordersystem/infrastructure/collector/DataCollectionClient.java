package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

/**
 * 완료 주문 데이터를 외부 수집 플랫폼으로 전달한다.
 */
public interface DataCollectionClient {

    void collect(long orderId, long userId, long menuId, long paymentAmount);
}
