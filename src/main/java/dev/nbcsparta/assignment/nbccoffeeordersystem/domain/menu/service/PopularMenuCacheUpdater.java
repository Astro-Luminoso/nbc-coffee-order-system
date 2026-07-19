package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.PopularityProjectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event.OrderCompletedEvent;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
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
 * 커밋된 주문을 일별 Redis ZSET 인기 메뉴 투영으로 반영하고 완료 상태를 기록한다.
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
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final PopularMenuDateLockManager dateLockManager;

    /**
     * 주문 투영에 필요한 Redis, 주문 저장소, 날짜 락 관리자를 주입한다.
     *
     * @param redisTemplate Redis ZSET 및 marker 저장소
     * @param coffeeOrderRepository 주문과 투영 상태 저장소
     * @param dateLockManager 날짜별 투영 직렬화 관리자
     */
    public PopularMenuCacheUpdater(
            StringRedisTemplate redisTemplate,
            CoffeeOrderRepository coffeeOrderRepository,
            PopularMenuDateLockManager dateLockManager
    ) {
        this.redisTemplate = redisTemplate;
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.dateLockManager = dateLockManager;
    }

    /**
     * 커밋된 주문을 해당 영업일의 Redis ZSET에 투영한다.
     *
     * @param event 커밋된 주문 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void project(OrderCompletedEvent event) {
        LocalDate date = event.orderedAt().atZone(BUSINESS_ZONE).toLocalDate();
        try {
            dateLockManager.withDateLock(date, () -> {
                coffeeOrderRepository.findById(event.orderId())
                        .ifPresent(this::projectOrder);
                return null;
            });
        } catch (RuntimeException exception) {
            log.error("완료 주문의 인기 메뉴 Redis 투영에 실패했습니다. 주문={}", event.orderId(), exception);
        }
    }

    /**
     * 지정한 영업일에 남아 있는 PENDING 투영을 날짜 락으로 보호하여 재시도한다.
     */
    public void projectPendingForDate(LocalDate date) {
        dateLockManager.withDateLock(date, () -> {
            projectPendingForDateWithinDateLock(date);
            return null;
        });
    }

    /**
     * 호출자가 해당 날짜의 락을 보유한 상태에서 PENDING 투영을 완료 처리한다.
     *
     * <p>재구축은 이 메서드를 먼저 호출한 뒤 SUCCEEDED 주문만 집계해야 한다.</p>
     */
    public void projectPendingForDateWithinDateLock(LocalDate date) {
        projectPendingForDateWithinDateLock(date, popularityKey(date));
    }

    /**
     * 호출자가 해당 날짜의 락을 보유한 상태에서 지정한 ZSET에 PENDING 투영을 완료 처리한다.
     *
     * <p>재구축은 임시 ZSET을 전달해 live ZSET을 변경하지 않은 채 PENDING 상태를 확정한다.</p>
     */
    public void projectPendingForDateWithinDateLock(LocalDate date, String targetZSetKey) {
        Instant from = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        coffeeOrderRepository
                .findAllByPopularityProjectionStatusAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByIdAsc(
                        PopularityProjectionStatus.PENDING, from, to
                )
                .forEach(order -> projectOrder(order, targetZSetKey));
    }

    private void projectOrder(CoffeeOrder order) {
        projectOrder(order, popularityKey(order.getOrderedAt()));
    }

    private void projectOrder(CoffeeOrder order, String targetZSetKey) {
        Long projected = redisTemplate.execute(
                INCREMENT_ONCE_SCRIPT,
                List.of("popular-menu:projection:" + order.getId(), targetZSetKey),
                "menu:" + order.getMenuId(), "1"
        );
        if (projected == null || (projected != 0L && projected != 1L)) {
            throw new IllegalStateException("인기 메뉴 Redis 투영 결과가 올바르지 않습니다.");
        }
        order.markPopularityProjectionSucceeded();
        coffeeOrderRepository.save(order);
    }

    private String popularityKey(Instant orderedAt) {
        LocalDate date = orderedAt.atZone(BUSINESS_ZONE).toLocalDate();
        return popularityKey(date);
    }

    private String popularityKey(LocalDate date) {
        return "popular-menu:" + date;
    }
}
