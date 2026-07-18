package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.controller;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto.CreateOrderAttemptRequest;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.dto.CreateOrderAttemptResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.service.OrderAttemptFacade;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 시도 생성 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/v1/order-attempts")
public class OrderAttemptController {

    private final OrderAttemptFacade orderAttemptFacade;

    /**
     * 주문 시도 파사드를 사용하는 컨트롤러를 생성한다.
     *
     * @param orderAttemptFacade 주문 시도 유스케이스 파사드
     */
    public OrderAttemptController(OrderAttemptFacade orderAttemptFacade) {
        this.orderAttemptFacade = orderAttemptFacade;
    }

    /**
     * 불변 주문 요청을 가진 대기 주문 시도를 생성한다.
     *
     * @param request 사용자와 메뉴 식별자 요청
     * @return 생성된 주문 시도 공통 응답
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<CreateOrderAttemptResponse>> createOrderAttempt(
            @Valid @RequestBody CreateOrderAttemptRequest request
    ) {
        CreateOrderAttemptResponse response = orderAttemptFacade.create(
                request.userId(),
                request.menuId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonApiResponse.of(HttpStatus.CREATED.value(), response));
    }
}
