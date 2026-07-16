package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 사용자 엔티티의 포인트 잔액 불변식을 검증한다.
 */
class UserTest {

    /**
     * 양수 포인트를 충전하면 잔액이 증가하는지 검증한다.
     */
    @Test
    void chargeIncreasesBalanceByPositiveAmount() {
        User user = new User(100L);

        user.charge(50L);

        assertThat(user.getBalance()).isEqualTo(150L);
    }

    /**
     * 음수 초기 잔액을 가진 사용자는 생성할 수 없는지 검증한다.
     */
    @Test
    void constructorRejectsNegativeBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new User(-1L));
    }

    /**
     * 양수가 아닌 포인트는 충전할 수 없는지 검증한다.
     */
    @Test
    void chargeRejectsNonPositiveAmount() {
        User user = new User(100L);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.charge(0L));
    }

    /**
     * 충전으로 표현 가능한 잔액 범위를 초과할 수 없는지 검증한다.
     */
    @Test
    void chargeRejectsBalanceOverflow() {
        User user = new User(Long.MAX_VALUE);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.charge(1L));
    }
}
