package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service.OrderCollectionDeliveryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 결제 데이터를 외부 플랫폼으로 전달한다.
 */
@Component
public class OrderCompletedEventListener {

    private final OrderCollectionDeliveryService orderCollectionDeliveryService;

    /**
     * 주문 데이터 전송 서비스를 사용하는 이벤트 리스너를 생성한다.
     *
     * @param orderCollectionDeliveryService 외부 수집 전송 서비스
     */
    public OrderCompletedEventListener(OrderCollectionDeliveryService orderCollectionDeliveryService) {
        this.orderCollectionDeliveryService = orderCollectionDeliveryService;
    }

    /**
     * 주문 트랜잭션이 커밋된 뒤 외부 데이터 수집 전송을 즉시 시도한다.
     *
     * @param event 커밋된 주문 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void collect(OrderCompletedEvent event) {
        orderCollectionDeliveryService.deliver(event.orderId());
    }
}
