package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector.DataCollectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료 주문의 외부 수집 플랫폼 전송을 처리한다.
 */
@Service
public class OrderCollectionDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(OrderCollectionDeliveryService.class);

    private final CoffeeOrderRepository coffeeOrderRepository;
    private final DataCollectionClient dataCollectionClient;

    /**
     * 주문 저장소와 외부 수집 클라이언트를 주입한다.
     *
     * @param coffeeOrderRepository 주문 저장소
     * @param dataCollectionClient 외부 데이터 수집 클라이언트
     */
    public OrderCollectionDeliveryService(
            CoffeeOrderRepository coffeeOrderRepository,
            DataCollectionClient dataCollectionClient
    ) {
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.dataCollectionClient = dataCollectionClient;
    }

    /**
     * 주문 데이터를 한 번 전송하고, 성공한 주문만 전송 완료 상태로 변경한다.
     *
     * @param orderId 전송할 주문 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(long orderId) {
        CoffeeOrder order = coffeeOrderRepository.findByIdAndCollectionStatusForUpdate(
                orderId, CollectionStatus.PENDING
        ).orElse(null);
        if (order == null) {
            return;
        }

        try {
            dataCollectionClient.collect(
                    order.getId(), order.getUserId(), order.getMenuId(), order.getPaymentAmount()
            );
            order.markCollectionSucceeded();
        } catch (RuntimeException exception) {
            log.error("주문 데이터 수집 전송에 실패했습니다. 주문={}", orderId, exception);
        }
    }
}
