package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

/**
 * 멱등성 레코드의 작업-키 복합 식별자를 표현한다.
 */
@Embeddable
public class IdempotencyRecordId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 32)
    private IdempotencyOperation operation;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /**
     * JPA가 복합 식별자를 생성할 때 사용하는 생성자이다.
     */
    protected IdempotencyRecordId() {
    }

    /**
     * 작업 종류와 클라이언트 키로 복합 식별자를 생성한다.
     *
     * @param operation 멱등성 작업 종류
     * @param idempotencyKey 클라이언트가 제공한 멱등성 키
     */
    public IdempotencyRecordId(IdempotencyOperation operation, String idempotencyKey) {
        this.operation = Objects.requireNonNull(operation);
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    }

    /**
     * 멱등성 작업 종류를 반환한다.
     *
     * @return 멱등성 작업 종류
     */
    public IdempotencyOperation getOperation() {
        return operation;
    }

    /**
     * 멱등성 키를 반환한다.
     *
     * @return 멱등성 키
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * 작업과 키가 같은 복합 식별자인지 비교한다.
     *
     * @param other 비교할 객체
     * @return 작업과 키가 모두 같으면 {@code true}
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyRecordId that)) {
            return false;
        }
        return operation == that.operation && idempotencyKey.equals(that.idempotencyKey);
    }

    /**
     * 작업과 키를 기반으로 해시 코드를 생성한다.
     *
     * @return 복합 식별자의 해시 코드
     */
    @Override
    public int hashCode() {
        return Objects.hash(operation, idempotencyKey);
    }
}
