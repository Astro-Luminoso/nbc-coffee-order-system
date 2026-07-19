package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.IdempotencyService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service.PreparedOrderPayment;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.MenuService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto.OrderPaymentResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event.OrderCompletedEvent;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.InternalServerErrorException;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 포인트 차감, 주문 생성, 멱등성 완료를 하나의 DB 트랜잭션으로 처리한다.
 */
@Service
public class OrderPaymentService {

    private final IdempotencyService idempotencyService;
    private final UserService userService;
    private final MenuService menuService;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 주문 결제 트랜잭션에 필요한 도메인 서비스와 저장소를 주입한다.
     *
     * @param idempotencyService 멱등성 상태 서비스
     * @param userService 사용자 포인트 서비스
     * @param menuService 메뉴 조회 서비스
     * @param coffeeOrderRepository 주문 저장소
     * @param eventPublisher 커밋 후 이벤트 발행기
     * @param objectMapper 완료 응답 직렬화 도구
     */
    public OrderPaymentService(
            IdempotencyService idempotencyService,
            UserService userService,
            MenuService menuService,
            CoffeeOrderRepository coffeeOrderRepository,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper
    ) {
        this.idempotencyService = idempotencyService;
        this.userService = userService;
        this.menuService = menuService;
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * 포인트 차감, 주문 생성, 멱등성 완료를 하나의 트랜잭션으로 처리한다.
     *
     * @param idempotencyKey 주문 시도 식별 키
     * @param userId 결제할 사용자 식별자
     * @param menuId 주문할 메뉴 식별자
     * @return 주문 결제 결과
     */
    @Transactional
    public OrderPaymentResponse pay(String idempotencyKey, long userId, long menuId) {
        PreparedOrderPayment prepared = idempotencyService.prepareOrderPayment(idempotencyKey, userId, menuId);
        if (prepared.completed()) {
            return readCompletedResponse(prepared.responseBody());
        }
        User user = userService.getUser(userId);
        Menu menu = menuService.getMenu(menuId);
        long remainingBalance = userService.deductIfSufficient(userId, menu.getPrice());
        CoffeeOrder order = coffeeOrderRepository.saveAndFlush(
                new CoffeeOrder(user, menu, menu.getPrice(), Instant.now())
        );
        OrderPaymentResponse response = OrderPaymentResponse.of(order, menu, remainingBalance);
        idempotencyService.completeOrderPayment(
                prepared, order.getId(), HttpStatus.CREATED.value(), writeCompletedResponse(response)
        );
        eventPublisher.publishEvent(new OrderCompletedEvent(
                order.getId(), userId, menuId, menu.getPrice(), order.getOrderedAt()
        ));
        return response;
    }

    private OrderPaymentResponse readCompletedResponse(String responseBody) {
        try {
            CommonApiResponse<OrderPaymentResponse> response = objectMapper.readValue(
                    responseBody, new TypeReference<CommonApiResponse<OrderPaymentResponse>>() { }
            );
            return response.data();
        } catch (JacksonException exception) {
            throw new InternalServerErrorException("저장된 주문 결제 결과를 처리할 수 없습니다.");
        }
    }

    private String writeCompletedResponse(OrderPaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(CommonApiResponse.of(HttpStatus.CREATED.value(), response));
        } catch (JacksonException exception) {
            throw new InternalServerErrorException("주문 결제 결과를 저장할 수 없습니다.");
        }
    }
}
