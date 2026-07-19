package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 주문 결제에 필요한 포인트가 부족한 경우 발생한다.
 */
public class InsufficientPointsException extends ServiceException {

    public InsufficientPointsException() {
        super("포인트 잔액이 부족합니다.", HttpStatus.CONFLICT);
    }
}
