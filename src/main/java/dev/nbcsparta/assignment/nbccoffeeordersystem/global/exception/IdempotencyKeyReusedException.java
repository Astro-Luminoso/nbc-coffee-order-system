package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 멱등성 키가 다른 정규 요청에 재사용되었을 때 발생하는 예외이다.
 */
public class IdempotencyKeyReusedException extends ServiceException {

    /**
     * 클라이언트에게 반환할 안전한 메시지로 예외를 생성한다.
     *
     * @param message 클라이언트에게 반환할 안전한 메시지
     */
    public IdempotencyKeyReusedException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
