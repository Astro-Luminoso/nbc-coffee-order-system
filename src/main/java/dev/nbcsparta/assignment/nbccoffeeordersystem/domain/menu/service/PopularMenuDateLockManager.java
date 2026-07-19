package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 같은 영업일의 인기 메뉴 투영과 재구축을 직렬화하는 Redis 분산 락을 제공한다.
 */
@Component
public class PopularMenuDateLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RETRY_DELAY = Duration.ofMillis(25);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis 기반 날짜 락 관리자를 생성한다.
     *
     * @param redisTemplate 날짜 락 저장소
     */
    public PopularMenuDateLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 날짜별 락을 보유한 상태로 작업을 실행한다.
     *
     * <p>작업은 락 TTL 안에 끝나야 하며, 락을 획득하지 못하면 예외를 발생시킨다.</p>
     */
    public <T> T withDateLock(LocalDate date, Supplier<T> action) {
        String lockKey = lockKey(date);
        String token = UUID.randomUUID().toString();
        acquire(lockKey, token);
        try {
            return action.get();
        } finally {
            release(lockKey, token);
        }
    }

    private void acquire(String lockKey, String token) {
        long deadline = System.nanoTime() + ACQUIRE_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL))) {
                return;
            }
            waitForRetry();
        }
        throw new IllegalStateException("인기 메뉴 날짜 락을 획득하지 못했습니다.");
    }

    private void waitForRetry() {
        try {
            Thread.sleep(RETRY_DELAY);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("인기 메뉴 날짜 락 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private void release(String lockKey, String token) {
        redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
    }

    private String lockKey(LocalDate date) {
        return "popular-menu:lock:" + date;
    }
}
