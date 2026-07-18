package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.CreatedOrderAttempt;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.MenuService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto.CreateOrderAttemptResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 시도의 존재 검증과 영속 생성을 조정한다.
 */
@Service
public class OrderAttemptFacade {

    private final UserService userService;
    private final MenuService menuService;
    private final IdempotencyService idempotencyService;

    /**
     * 주문 시도 생성에 필요한 서비스를 사용한다.
     *
     * @param userService 사용자 존재를 확인하는 서비스
     * @param menuService 메뉴 존재를 확인하는 서비스
     * @param idempotencyService 주문 시도를 영속하는 서비스
     */
    public OrderAttemptFacade(
            UserService userService,
            MenuService menuService,
            IdempotencyService idempotencyService
    ) {
        this.userService = userService;
        this.menuService = menuService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * 사용자와 메뉴를 확인한 뒤 대기 상태의 주문 시도를 영속한다.
     *
     * <p>이 단계에서는 포인트를 차감하거나 주문, 주문 항목, 아웃박스를 만들지 않는다.</p>
     *
     * @param userId 주문할 사용자 식별자
     * @param menuId 주문할 메뉴 식별자
     * @return 생성된 대기 주문 시도 응답
     */
    @Transactional
    public CreateOrderAttemptResponse create(long userId, long menuId) {
        userService.getUser(userId);
        menuService.getMenu(menuId);
        CreatedOrderAttempt createdOrderAttempt = idempotencyService.createOrderAttempt(userId, menuId);
        return CreateOrderAttemptResponse.from(createdOrderAttempt);
    }
}
