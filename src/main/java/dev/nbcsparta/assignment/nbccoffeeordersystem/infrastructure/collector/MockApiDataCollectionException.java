package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

/**
 * MockAPI.io 주문 수집에 실패했을 때 발생한다.
 */
public class MockApiDataCollectionException extends RuntimeException {

    /**
     * 실패 사유를 전달한다.
     *
     * @param message 실패 사유
     */
    public MockApiDataCollectionException(String message) {
        super(message);
    }

}
