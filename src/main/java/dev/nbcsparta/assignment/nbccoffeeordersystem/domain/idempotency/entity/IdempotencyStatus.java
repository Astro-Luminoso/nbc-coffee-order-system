package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity;

/**
 * 멱등성 작업의 처리 상태를 표현한다.
 */
public enum IdempotencyStatus {

    PENDING,
    COMPLETED
}
