package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 요청한 메뉴를 찾을 수 없을 때 발생하는 예외이다.
 */
public class MenuNotFoundException extends ServiceException {

    /**
     * 메뉴 식별자로 메뉴 미존재 예외를 생성한다.
     *
     * @param menuId 찾을 수 없는 메뉴 식별자
     */
    public MenuNotFoundException(long menuId) {
        super("요청한 메뉴를 찾을 수 없습니다. 메뉴 식별자: " + menuId, HttpStatus.NOT_FOUND);
    }
}
