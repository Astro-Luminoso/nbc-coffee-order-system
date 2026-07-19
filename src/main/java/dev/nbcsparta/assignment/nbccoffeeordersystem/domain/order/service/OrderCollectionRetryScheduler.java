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

    /**
     * 수집 전송 재시도에 필요한 저장소와 서비스를 주입한다.
     *
     * @param coffeeOrderRepository 주문 저장소
     * @param orderCollectionDeliveryService 외부 수집 전송 서비스
     */
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
