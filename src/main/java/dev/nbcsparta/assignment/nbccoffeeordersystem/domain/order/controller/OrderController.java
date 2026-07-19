package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.controller;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto.CreateOrderRequest;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto.OrderPaymentResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service.OrderPaymentFacade;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 단일 메뉴 주문과 포인트 결제 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderPaymentFacade orderPaymentFacade;

    /**
     * 주문 결제 유스케이스를 사용하는 컨트롤러를 생성한다.
     *
     * @param orderPaymentFacade 주문 결제 파사드
     */
    public OrderController(OrderPaymentFacade orderPaymentFacade) {
        this.orderPaymentFacade = orderPaymentFacade;
    }

    /**
     * 사용자 포인트로 메뉴를 주문하고 결제한다.
     *
     * @param idempotencyKey 재시도를 식별하는 멱등성 키
     * @param request 사용자와 메뉴를 포함한 주문 요청
     * @return 생성된 주문 결제 공통 응답
     */
    @PostMapping
    public ResponseEntity<CommonApiResponse<OrderPaymentResponse>> createOrder(
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "멱등성 키는 필수입니다.")
            @Size(max = 128, message = "멱등성 키는 128자 이하여야 합니다.")
            @Pattern(regexp = "^[\\x20-\\x7E]+$", message = "멱등성 키는 ASCII 문자열이어야 합니다.")
            String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderPaymentResponse response = orderPaymentFacade.pay(idempotencyKey, request.userId(), request.menuId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonApiResponse.of(HttpStatus.CREATED.value(), response));
    }
}
