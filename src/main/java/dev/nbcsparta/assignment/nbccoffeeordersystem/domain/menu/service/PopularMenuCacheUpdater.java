package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event.OrderCompletedEvent;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 주문을 일별 Redis ZSET 인기 메뉴 투영으로 반영한다.
 */
@Component
public class PopularMenuCacheUpdater {

    private static final Logger log = LoggerFactory.getLogger(PopularMenuCacheUpdater.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final DefaultRedisScript<Long> INCREMENT_ONCE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('SETNX', KEYS[1], '1') == 1 then "
                    + "redis.call('ZINCRBY', KEYS[2], ARGV[2], ARGV[1]); return 1; end; return 0;",
            Long.class
    );
    private final StringRedisTemplate redisTemplate;

    public PopularMenuCacheUpdater(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void project(OrderCompletedEvent event) {
        String date = event.orderedAt().atZone(BUSINESS_ZONE).toLocalDate().toString();
        try {
            redisTemplate.execute(
                    INCREMENT_ONCE_SCRIPT,
                    List.of("popular-menu:projection:" + event.orderId(), "popular-menu:" + date),
                    "menu:" + event.menuId(), "1"
            );
        } catch (RuntimeException exception) {
            log.error("완료 주문의 인기 메뉴 캐시 반영에 실패했습니다. 주문={}", event.orderId(), exception);
        }
    }
}
