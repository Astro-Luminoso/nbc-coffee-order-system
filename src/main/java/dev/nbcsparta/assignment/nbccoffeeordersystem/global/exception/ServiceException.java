package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 계층에서 클라이언트에게 안전하게 반환할 예외의 기반 타입이다.
 */
public abstract class ServiceException extends RuntimeException {

    private final HttpStatus httpStatus;

    /**
     * 클라이언트 메시지와 HTTP 상태로 예외를 생성한다.
     *
     * @param message 클라이언트에게 반환할 안전한 메시지
     * @param httpStatus 클라이언트에게 반환할 HTTP 상태
     */
    protected ServiceException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * 클라이언트에게 반환할 HTTP 상태를 반환한다.
     *
     * @return HTTP 상태
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
