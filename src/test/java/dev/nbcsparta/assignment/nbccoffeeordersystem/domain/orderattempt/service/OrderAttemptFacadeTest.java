package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.CreatedOrderAttempt;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.MenuService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto.CreateOrderAttemptResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 주문 시도 생성 유스케이스 조정을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OrderAttemptFacadeTest {

    @Mock
    private UserService userService;

    @Mock
    private MenuService menuService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private OrderAttemptFacade orderAttemptFacade;

    /**
     * 존재가 확인된 사용자와 메뉴로 대기 시도를 영속하고 API 응답으로 변환하는지 검증한다.
     */
    @Test
    void createPersistsPendingAttemptAfterExistenceChecks() {
        Instant expiresAt = Instant.parse("2026-07-18T06:00:00Z");
        when(idempotencyService.createOrderAttempt(1L, 2L))
                .thenReturn(new CreatedOrderAttempt("attempt-1", expiresAt));

        CreateOrderAttemptResponse response = orderAttemptFacade.create(1L, 2L);

        assertThat(response.orderAttemptId()).isEqualTo("attempt-1");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.expiresAt()).isEqualTo(OffsetDateTime.parse("2026-07-18T15:00:00+09:00"));
        verify(userService).getUser(1L);
        verify(menuService).getMenu(2L);
        verify(idempotencyService).createOrderAttempt(1L, 2L);
    }

    /**
     * 사용자 검증에 실패하면 메뉴 조회와 주문 시도 영속을 수행하지 않는지 검증한다.
     */
    @Test
    void createDoesNotPersistWhenUserDoesNotExist() {
        RuntimeException exception = new RuntimeException("사용자가 없습니다.");
        when(userService.getUser(1L)).thenThrow(exception);

        assertThatThrownBy(() -> orderAttemptFacade.create(1L, 2L))
                .isSameAs(exception);

        verify(menuService, never()).getMenu(2L);
        verify(idempotencyService, never()).createOrderAttempt(1L, 2L);
    }

    /**
     * 메뉴 검증에 실패하면 주문 시도 영속을 수행하지 않는지 검증한다.
     */
    @Test
    void createDoesNotPersistWhenMenuDoesNotExist() {
        RuntimeException exception = new RuntimeException("메뉴가 없습니다.");
        when(menuService.getMenu(2L)).thenThrow(exception);

        assertThatThrownBy(() -> orderAttemptFacade.create(1L, 2L))
                .isSameAs(exception);

        verify(userService).getUser(1L);
        verify(idempotencyService, never()).createOrderAttempt(1L, 2L);
    }
}
