package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 완료된 주문 결제의 API 응답을 표현한다.
 */
public record OrderPaymentResponse(
        Long orderId,
        Long userId,
        MenuResponse menu,
        Long paymentAmount,
        Long remainingBalance,
        OffsetDateTime orderedAt
) {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 주문과 메뉴, 결제 후 잔액으로 결제 응답을 생성한다.
     *
     * @param order 완료된 주문
     * @param menu 결제한 메뉴
     * @param remainingBalance 결제 후 남은 포인트
     * @return 주문 결제 응답
     */
    public static OrderPaymentResponse of(CoffeeOrder order, Menu menu, long remainingBalance) {
        return new OrderPaymentResponse(
                order.getId(), order.getUserId(), MenuResponse.from(menu), order.getPaymentAmount(),
                remainingBalance, order.getOrderedAt().atZone(BUSINESS_ZONE).toOffsetDateTime()
        );
    }

    /**
     * 결제 응답에 포함되는 메뉴 요약 정보다.
     */
    public record MenuResponse(Long id, String name) {

        /**
         * 메뉴 엔티티를 응답용 요약 정보로 변환한다.
         *
         * @param menu 변환할 메뉴
         * @return 메뉴 요약 정보
         */
        public static MenuResponse from(Menu menu) {
            return new MenuResponse(menu.getId(), menu.getName());
        }
    }
}
