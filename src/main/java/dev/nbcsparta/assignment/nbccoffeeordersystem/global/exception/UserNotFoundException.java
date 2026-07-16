package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청한 사용자를 찾을 수 없을 때 발생하는 예외이다.
 */
public class UserNotFoundException extends ServiceException {

    /**
     * 사용자 식별자로 사용자 미존재 예외를 생성한다.
     *
     * @param userId 찾을 수 없는 사용자 식별자
     */
    public UserNotFoundException(long userId) {
        super("요청한 사용자를 찾을 수 없습니다. 사용자 식별자: " + userId, HttpStatus.NOT_FOUND);
    }
}
