package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity;

/**
 * 결제 완료 주문의 Redis 인기 메뉴 투영 상태를 표현한다.
 */
public enum PopularityProjectionStatus {

    /** Redis ZSET 반영을 아직 완료하지 못한 상태다. */
    PENDING,

    /** Redis ZSET 반영과 상태 저장을 완료한 상태다. */
    SUCCEEDED
}
