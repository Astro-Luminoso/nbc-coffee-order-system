package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자의 포인트 잔액을 표현하는 영속 엔티티이다.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "point_balance", nullable = false, columnDefinition = "BIGINT CHECK (point_balance >= 0)")
    private long pointBalance;

    /**
     * JPA가 엔티티를 생성할 때 사용하는 생성자이다.
     */
    protected User() {
    }

    /**
     * 초기 포인트 잔액으로 사용자를 생성한다.
     *
     * @param balance 초기 포인트 잔액
     */
    public User(long balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("초기 포인트 잔액은 음수일 수 없습니다.");
        }
        this.pointBalance = balance;
    }

    /**
     * 사용자 식별자를 반환한다.
     *
     * @return 사용자 식별자
     */
    public Long getId() {
        return id;
    }

    /**
     * 현재 포인트 잔액을 반환한다.
     *
     * @return 현재 포인트 잔액
     */
    public long getPointBalance() {
        return pointBalance;
    }

    /**
     * 양수 포인트를 충전한다.
     *
     * @param amount 충전할 포인트
     * @throws IllegalArgumentException 충전 금액이 양수가 아니거나 잔액 범위를 초과한 경우
     */
    public void charge(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 포인트는 양수여야 합니다.");
        }
        if (pointBalance > Long.MAX_VALUE - amount) {
            throw new IllegalArgumentException("충전 후 포인트 잔액이 허용 범위를 초과합니다.");
        }
        pointBalance += amount;
    }

    /**
     * 기존 호출부 호환을 위해 현재 포인트 잔액을 반환한다.
     *
     * @return 현재 포인트 잔액
     */
    public long getBalance() {
        return getPointBalance();
    }
}
