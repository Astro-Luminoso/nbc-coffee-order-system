package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event;

import java.time.Instant;

/**
 * DB 트랜잭션이 커밋된 주문 결제 정보를 전달한다.
 */
public record OrderCompletedEvent(long orderId, long userId, long menuId, long paymentAmount, Instant orderedAt) {
}
