package dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception;

import org.springframework.http.HttpStatus;

/**
 * Redis 기반 인기 메뉴 조회를 수행할 수 없을 때 발생한다.
 */
public class RedisUnavailableException extends ServiceException {

    /**
     * Redis 기반 인기 메뉴 조회의 일시적 장애 예외를 생성한다.
     */
    public RedisUnavailableException() {
        super("인기 메뉴 정보를 일시적으로 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
