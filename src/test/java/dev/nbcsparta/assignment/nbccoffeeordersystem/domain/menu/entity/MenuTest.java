package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * 메뉴 생성 시 입력 불변식을 검증한다.
 */
class MenuTest {

    @Test
    void constructorRejectsBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Menu(" ", 4_500L));
    }

    @Test
    void constructorRejectsNonPositivePrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Menu("Americano", 0L));
    }
}
