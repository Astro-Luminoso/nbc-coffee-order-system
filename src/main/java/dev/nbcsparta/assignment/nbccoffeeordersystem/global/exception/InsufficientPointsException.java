package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 주문 결제에 필요한 포인트가 부족한 경우 발생한다.
 */
public class InsufficientPointsException extends ServiceException {

    /**
     * 포인트 부족 오류 응답을 위한 예외를 생성한다.
     */
    public InsufficientPointsException() {
        super("포인트 잔액이 부족합니다.", HttpStatus.CONFLICT);
    }
}
