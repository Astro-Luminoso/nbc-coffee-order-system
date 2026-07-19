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

    public OrderCompletedEventListener(OrderCollectionDeliveryService orderCollectionDeliveryService) {
        this.orderCollectionDeliveryService = orderCollectionDeliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void collect(OrderCompletedEvent event) {
        orderCollectionDeliveryService.deliver(event.orderId());
    }
}
