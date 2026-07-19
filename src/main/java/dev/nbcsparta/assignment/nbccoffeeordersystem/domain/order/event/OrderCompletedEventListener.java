package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event;

import dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector.DataCollectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 결제 데이터를 외부 플랫폼으로 전달한다.
 */
@Component
public class OrderCompletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCompletedEventListener.class);
    private final DataCollectionClient dataCollectionClient;

    public OrderCompletedEventListener(DataCollectionClient dataCollectionClient) {
        this.dataCollectionClient = dataCollectionClient;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void collect(OrderCompletedEvent event) {
        try {
            dataCollectionClient.collect(event.userId(), event.menuId(), event.paymentAmount());
        } catch (RuntimeException exception) {
            log.error("커밋된 주문 데이터 수집 전송에 실패했습니다. 주문={}", event.orderId(), exception);
        }
    }
}
