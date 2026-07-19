package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse.PopularMenuResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.MenuOrderCount;
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
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Redis 일별 투영을 읽고 필요하면 MySQL 주문 집계로 복구해 인기 메뉴를 제공한다.
 */
@Service
public class PopularMenuService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PERIOD_DAYS = 7;
    private final StringRedisTemplate redisTemplate;
    private final CoffeeOrderRepository coffeeOrderRepository;
    private final MenuRepository menuRepository;
    private final Clock clock;

    @Autowired
    public PopularMenuService(
            StringRedisTemplate redisTemplate,
            CoffeeOrderRepository coffeeOrderRepository,
            MenuRepository menuRepository
    ) {
        this(redisTemplate, coffeeOrderRepository, menuRepository, Clock.system(BUSINESS_ZONE));
    }

    PopularMenuService(
            StringRedisTemplate redisTemplate,
            CoffeeOrderRepository coffeeOrderRepository,
            MenuRepository menuRepository,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.menuRepository = menuRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PopularMenuListResponse getPopularMenus() {
        LocalDate periodEnd = LocalDate.now(clock).minusDays(1);
        LocalDate periodStart = periodEnd.minusDays(PERIOD_DAYS - 1);
        Map<Long, Long> counts = readCacheOrRebuild(periodStart, periodEnd);
        List<PopularMenuResponse> menus = counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> menuRepository.findById(entry.getKey())
                        .map(menu -> PopularMenuResponse.of(menu, entry.getValue())))
                .flatMap(java.util.Optional::stream)
                .toList();
        return new PopularMenuListResponse(periodStart, periodEnd, menus);
    }

    private Map<Long, Long> readCacheOrRebuild(LocalDate start, LocalDate end) {
        try {
            Map<Long, Long> counts = new HashMap<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                String key = key(date);
                if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                    return rebuildFromDatabase(start, end);
                }
                Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
                if (tuples == null) {
                    return rebuildFromDatabase(start, end);
                }
                for (TypedTuple<String> tuple : tuples) {
                    Long menuId = menuId(tuple.getValue());
                    Double score = tuple.getScore();
                    if (menuId == null || score == null || score < 0 || score != Math.rint(score)) {
                        return rebuildFromDatabase(start, end);
                    }
                    counts.merge(menuId, score.longValue(), Long::sum);
                }
            }
            return counts;
        } catch (RuntimeException exception) {
            return aggregateFromDatabase(start, end);
        }
    }

    private Map<Long, Long> rebuildFromDatabase(LocalDate start, LocalDate end) {
        Map<Long, Long> periodCounts = aggregateFromDatabase(start, end);
        try {
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                Map<Long, Long> dailyCounts = aggregateFromDatabase(date, date);
                String key = key(date);
                redisTemplate.delete(key);
                dailyCounts.forEach((menuId, count) -> redisTemplate.opsForZSet().add(key, "menu:" + menuId, count));
            }
        } catch (RuntimeException ignored) {
            // Redis 장애는 MySQL에서 계산한 응답을 반환하는 데 영향을 주지 않는다.
        }
        return periodCounts;
    }

    private Map<Long, Long> aggregateFromDatabase(LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return coffeeOrderRepository.aggregateMenuCounts(from, to).stream()
                .collect(java.util.stream.Collectors.toMap(MenuOrderCount::getMenuId, MenuOrderCount::getOrderCount));
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
}
