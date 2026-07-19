package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 전송이 완료되지 않은 주문을 주기적으로 다시 전달한다.
 */
@Component
public class OrderCollectionRetryScheduler {

    private final CoffeeOrderRepository coffeeOrderRepository;
    private final OrderCollectionDeliveryService orderCollectionDeliveryService;

    public OrderCollectionRetryScheduler(
            CoffeeOrderRepository coffeeOrderRepository,
            OrderCollectionDeliveryService orderCollectionDeliveryService
    ) {
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.orderCollectionDeliveryService = orderCollectionDeliveryService;
    }

    /**
     * 기본 1분 간격으로 전송 대기 주문을 재시도한다.
     */
    @Scheduled(
            fixedDelayString = "${collection.delivery.retry-delay:60000}",
            initialDelayString = "${collection.delivery.initial-retry-delay:60000}"
    )
    public void retryPendingDeliveries() {
        coffeeOrderRepository.findAllByCollectionStatus(CollectionStatus.PENDING)
                .forEach(order -> orderCollectionDeliveryService.deliver(order.getId()));
    }
}
