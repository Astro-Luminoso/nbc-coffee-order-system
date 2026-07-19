package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity;

/**
 * 멱등성 레코드가 보호하는 작업의 종류를 표현한다.
 */
public enum IdempotencyOperation {

    POINT_CHARGE,
    ORDER_PAYMENT
}
