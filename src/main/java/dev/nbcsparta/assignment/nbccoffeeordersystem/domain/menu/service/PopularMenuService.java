package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse.PopularMenuResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.RedisUnavailableException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Redis 일별 투영을 읽고 필요하면 MySQL 주문 집계로 복구해 인기 메뉴를 제공한다.
 */
@Service
public class PopularMenuService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PERIOD_DAYS = 7;
    private final StringRedisTemplate redisTemplate;
    private final MenuRepository menuRepository;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final PopularMenuCacheUpdater popularMenuCacheUpdater;
    private final PopularMenuCacheRebuilder popularMenuCacheRebuilder;
    private final PopularMenuDateLockManager dateLockManager;
    private final Clock clock;

    /**
     * 인기 메뉴 조회에 필요한 Redis와 도메인 서비스를 주입한다.
     *
     * @param redisTemplate 인기 메뉴 ZSET 저장소
     * @param menuRepository 메뉴 조회 저장소
     * @param coffeeOrderRepository Redis 장애 시 주문 집계 저장소
     * @param popularMenuCacheUpdater 미완료 투영 처리기
     * @param popularMenuCacheRebuilder 손상 캐시 재구축기
     * @param dateLockManager 날짜별 Redis 락 관리자
     */
    @Autowired
    public PopularMenuService(
            StringRedisTemplate redisTemplate,
            MenuRepository menuRepository,
            CoffeeOrderRepository coffeeOrderRepository,
            PopularMenuCacheUpdater popularMenuCacheUpdater,
            PopularMenuCacheRebuilder popularMenuCacheRebuilder,
            PopularMenuDateLockManager dateLockManager
    ) {
        this(
                redisTemplate,
                menuRepository,
                coffeeOrderRepository,
                popularMenuCacheUpdater,
                popularMenuCacheRebuilder,
                dateLockManager,
                Clock.system(BUSINESS_ZONE)
        );
    }

    PopularMenuService(
            StringRedisTemplate redisTemplate,
            MenuRepository menuRepository,
            CoffeeOrderRepository coffeeOrderRepository,
            PopularMenuCacheUpdater popularMenuCacheUpdater,
            PopularMenuCacheRebuilder popularMenuCacheRebuilder,
            PopularMenuDateLockManager dateLockManager,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.menuRepository = menuRepository;
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.popularMenuCacheUpdater = popularMenuCacheUpdater;
        this.popularMenuCacheRebuilder = popularMenuCacheRebuilder;
        this.dateLockManager = dateLockManager;
        this.clock = clock;
    }

    /**
     * 직전 완료 7일의 Redis ZSET을 합산해 인기 메뉴 최대 세 건을 반환한다.
     * Redis를 사용할 수 없으면 MySQL 주문 집계로 응답을 만든다.
     *
     * @return 인기 메뉴 목록
     */
    public PopularMenuListResponse getPopularMenus() {
        LocalDate periodEnd = LocalDate.now(clock).minusDays(1);
        LocalDate periodStart = periodEnd.minusDays(PERIOD_DAYS - 1);
        Map<Long, Long> counts;
        try {
            counts = readRedisCounts(periodStart, periodEnd);
        } catch (RedisUnavailableException exception) {
            counts = readMySqlCounts(periodStart, periodEnd);
        }
        List<PopularMenuResponse> menus = counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> menuRepository.findById(entry.getKey())
                        .map(menu -> PopularMenuResponse.of(menu, entry.getValue())))
                .flatMap(java.util.Optional::stream)
                .toList();
        return new PopularMenuListResponse(periodStart, periodEnd, menus);
    }

    private Map<Long, Long> readMySqlCounts(LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Map<Long, Long> counts = new HashMap<>();
        coffeeOrderRepository.aggregateMenuCounts(from, to).forEach(menuOrderCount ->
                counts.merge(menuOrderCount.getMenuId(), menuOrderCount.getOrderCount(), Long::sum)
        );
        return counts;
    }

    private Map<Long, Long> readRedisCounts(LocalDate start, LocalDate end) {
        Map<Long, Long> counts = new HashMap<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            Map<Long, Long> dailyCounts = readRedisCountsForDate(date);
            dailyCounts.forEach((menuId, count) -> counts.merge(menuId, count, Long::sum));
        }
        return counts;
    }

    private Map<Long, Long> readRedisCountsForDate(LocalDate date) {
        try {
            return dateLockManager.withDateLock(date, () -> {
                CacheReadResult cacheReadResult = readDailyCache(date);
                if (!cacheReadResult.valid()) {
                    popularMenuCacheRebuilder.rebuildWithinDateLock(date);
                    cacheReadResult = readDailyCache(date);
                } else {
                    popularMenuCacheUpdater.projectPendingForDateWithinDateLock(date);
                    cacheReadResult = readDailyCache(date);
                    if (!cacheReadResult.valid()) {
                        popularMenuCacheRebuilder.rebuildWithinDateLock(date);
                        cacheReadResult = readDailyCache(date);
                    }
                }

                if (!cacheReadResult.valid()) {
                    throw new IllegalStateException("인기 메뉴 Redis ZSET 복구 결과가 올바르지 않습니다.");
                }
                return cacheReadResult.counts();
            });
        } catch (RedisUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RedisUnavailableException();
        }
    }

    private CacheReadResult readDailyCache(LocalDate date) {
        String key = key(date);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return CacheReadResult.invalid();
        }
        if (redisTemplate.type(key) != DataType.ZSET) {
            return CacheReadResult.invalid();
        }

        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples == null) {
            return CacheReadResult.invalid();
        }

        Map<Long, Long> counts = new HashMap<>();
        for (TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            Double score = tuple.getScore();
            if ("cache:ready".equals(member)) {
                if (score == null || score != 0D) {
                    return CacheReadResult.invalid();
                }
                continue;
            }

            Long menuId = menuId(member);
            if (menuId == null || score == null || score < 1D || score > Long.MAX_VALUE || score != Math.rint(score)) {
                return CacheReadResult.invalid();
            }
            counts.merge(menuId, score.longValue(), Long::sum);
        }
        return CacheReadResult.valid(counts);
    }

    private String key(LocalDate date) {
        return "popular-menu:" + date;
    }

    private Long menuId(String value) {
        if (value == null || !value.startsWith("menu:")) {
            return null;
        }
        try {
            long id = Long.parseLong(value.substring("menu:".length()));
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record CacheReadResult(boolean valid, Map<Long, Long> counts) {

        private static CacheReadResult valid(Map<Long, Long> counts) {
            return new CacheReadResult(true, Map.copyOf(counts));
        }

        private static CacheReadResult invalid() {
            return new CacheReadResult(false, Map.of());
        }
    }
}
