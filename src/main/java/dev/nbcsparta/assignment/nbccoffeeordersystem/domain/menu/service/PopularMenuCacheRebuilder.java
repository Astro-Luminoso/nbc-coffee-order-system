package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.PopularityProjectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.MenuOrderCount;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 손상되거나 누락된 일별 인기 메뉴 ZSET을 MySQL 투영 완료 주문으로 재구축한다.
 */
@Component
public class PopularMenuCacheRebuilder {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final String READY_MEMBER = "cache:ready";

    private final StringRedisTemplate redisTemplate;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final PopularMenuCacheUpdater popularMenuCacheUpdater;

    /**
     * 캐시 재구축에 필요한 저장소와 투영 처리기를 주입한다.
     *
     * @param redisTemplate 임시 및 live ZSET 저장소
     * @param coffeeOrderRepository 투영 완료 주문 집계 저장소
     * @param popularMenuCacheUpdater PENDING 주문 투영 처리기
     */
    public PopularMenuCacheRebuilder(
            StringRedisTemplate redisTemplate,
            CoffeeOrderRepository coffeeOrderRepository,
            PopularMenuCacheUpdater popularMenuCacheUpdater
    ) {
        this.redisTemplate = redisTemplate;
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.popularMenuCacheUpdater = popularMenuCacheUpdater;
    }

    /**
     * 호출자가 해당 날짜의 공유 락을 보유한 상태에서 캐시를 재구축한다.
     *
     * <p>PENDING 투영과 SUCCEEDED 주문 집계를 임시 키에서 완료한 뒤
     * RENAME으로 live 키를 교체하므로, 재구축 실패는 기존 캐시를 훼손하지 않는다.</p>
     *
     * @param date 재구축할 서울 영업일
     */
    public void rebuildWithinDateLock(LocalDate date) {
        String liveKey = popularityKey(date);
        String temporaryKey = liveKey + ":rebuild:" + UUID.randomUUID();
        writeTemporaryZSet(temporaryKey);
        popularMenuCacheUpdater.projectPendingForDateWithinDateLock(date, temporaryKey);
        writeSucceededMenuCounts(temporaryKey, date);
        redisTemplate.rename(temporaryKey, liveKey);
    }

    private void writeTemporaryZSet(String temporaryKey) {
        redisTemplate.opsForZSet().add(temporaryKey, READY_MEMBER, 0D);
    }

    private void writeSucceededMenuCounts(String temporaryKey, LocalDate date) {
        aggregateSucceededMenuCounts(date).forEach(menuOrderCount -> redisTemplate.opsForZSet().add(
                temporaryKey,
                "menu:" + menuOrderCount.getMenuId(),
                menuOrderCount.getOrderCount().doubleValue()
        ));
    }

    private List<MenuOrderCount> aggregateSucceededMenuCounts(LocalDate date) {
        Instant from = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return coffeeOrderRepository.aggregateMenuCountsByPopularityProjectionStatus(
                from,
                to,
                PopularityProjectionStatus.SUCCEEDED
        );
    }

    private String popularityKey(LocalDate date) {
        return "popular-menu:" + date;
    }
}
