package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 별도 수집 플랫폼이 설정되기 전 사용하는 기본 수집 클라이언트다.
 */
@Component
public class LoggingDataCollectionClient implements DataCollectionClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingDataCollectionClient.class);

    /**
     * 외부 수집 플랫폼을 대체하는 로그 기반 전송을 수행한다.
     *
     * @param orderId 주문 식별자
     * @param userId 사용자 식별자
     * @param menuId 메뉴 식별자
     * @param paymentAmount 결제 금액
     */
    @Override
    public void collect(long orderId, long userId, long menuId, long paymentAmount) {
        log.info("주문 데이터 수집을 요청했습니다. 주문={}, 사용자={}, 메뉴={}, 결제금액={}", orderId, userId, menuId, paymentAmount);
    }
}
