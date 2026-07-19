package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository;

/**
 * 메뉴별 결제 주문 집계 결과다.
 */
public interface MenuOrderCount {

    Long getMenuId();

    Long getOrderCount();
}
