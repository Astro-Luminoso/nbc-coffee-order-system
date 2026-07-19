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

    public static OrderPaymentResponse of(CoffeeOrder order, Menu menu, long remainingBalance) {
        return new OrderPaymentResponse(
                order.getId(), order.getUserId(), MenuResponse.from(menu), order.getPaymentAmount(),
                remainingBalance, order.getOrderedAt().atZone(BUSINESS_ZONE).toOffsetDateTime()
        );
    }

    public record MenuResponse(Long id, String name) {
        public static MenuResponse from(Menu menu) {
            return new MenuResponse(menu.getId(), menu.getName());
        }
    }
}
