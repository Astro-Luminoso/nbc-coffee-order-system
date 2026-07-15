package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 예상하지 못한 서버 오류가 발생했을 때 사용하는 예외이다.
 */
public class InternalServerErrorException extends ServiceException {

    /**
     * 안전한 서버 오류 메시지로 예외를 생성한다.
     *
     * @param message 클라이언트에게 반환할 안전한 메시지
     */
    public InternalServerErrorException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
