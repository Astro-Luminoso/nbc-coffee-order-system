package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.MenuOrderCount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * 인기 메뉴 API가 MySQL 계산값이 아니라 Redis ZSET 최종 읽기를 반환하는지 검증한다.
 */
class PopularMenuServiceIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 18);

    /**
     * 누락된 일별 키는 재구축한 뒤 Redis를 다시 읽고, 현재 날짜를 제외한 7일 순위만 반환한다.
     */
    @Test
    void rebuildsMissingKeysThenReadsRedisForSevenCompletedDaysAndRanksWithMenuIdTieBreaker() {
        RedisScenario scenario = new RedisScenario();
        PopularMenuCacheRebuilder rebuilder = mock(PopularMenuCacheRebuilder.class);
        doAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            scenario.put(date, date.equals(PERIOD_END) ? Map.of(2L, 2L, 1L, 1L, 3L, 1L) : Map.of());
            return null;
        }).when(rebuilder).rebuildWithinDateLock(any(LocalDate.class));

        PopularMenuService service = service(scenario, mock(PopularMenuCacheUpdater.class), rebuilder);

        PopularMenuListResponse response = service.getPopularMenus();

        assertThat(response.periodStartDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(response.periodEndDate()).isEqualTo(PERIOD_END);
        assertThat(response.menus())
                .extracting(PopularMenuListResponse.PopularMenuResponse::menuId,
                        PopularMenuListResponse.PopularMenuResponse::orderCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2L, 2L),
                        org.assertj.core.groups.Tuple.tuple(1L, 1L),
                        org.assertj.core.groups.Tuple.tuple(3L, 1L)
                );
        verify(rebuilder, org.mockito.Mockito.times(7)).rebuildWithinDateLock(any(LocalDate.class));
    }

    /**
     * 기존 ZSET이 있어도 PENDING 주문이 있으면 먼저 보정한 뒤의 Redis 점수를 응답에 사용한다.
     */
    @Test
    void correctsPendingProjectionBeforeTrustingAnExistingZSet() {
        RedisScenario scenario = new RedisScenario();
        for (LocalDate date = LocalDate.of(2026, 7, 12); !date.isAfter(PERIOD_END); date = date.plusDays(1)) {
            scenario.put(date, date.equals(PERIOD_END) ? Map.of(1L, 1L) : Map.of());
        }
        PopularMenuCacheUpdater updater = mock(PopularMenuCacheUpdater.class);
        doAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            if (date.equals(PERIOD_END)) {
                scenario.put(PERIOD_END, Map.of(1L, 2L));
            }
            return null;
        }).when(updater).projectPendingForDateWithinDateLock(any(LocalDate.class));

        PopularMenuService service = service(scenario, updater, mock(PopularMenuCacheRebuilder.class));

        PopularMenuListResponse response = service.getPopularMenus();

        assertThat(response.menus()).singleElement()
                .extracting(PopularMenuListResponse.PopularMenuResponse::menuId,
                        PopularMenuListResponse.PopularMenuResponse::orderCount)
                .containsExactly(1L, 2L);
        verify(updater, org.mockito.Mockito.times(7)).projectPendingForDateWithinDateLock(any(LocalDate.class));
    }

    /**
     * 임시 ZSET 작성이 실패하면 atomic swap 이전에 중단되어 기존 live 키를 보존한다.
     */
    @Test
    void keepsExistingLiveZSetWhenTemporaryRebuildFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(any(String.class), any(String.class), anyDouble()))
                .thenThrow(new IllegalStateException("임시 ZSET 쓰기 실패"));
        PopularMenuCacheRebuilder rebuilder = new PopularMenuCacheRebuilder(
                redisTemplate,
                mock(dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository.class),
                mock(PopularMenuCacheUpdater.class)
        );

        assertThatThrownBy(() -> rebuilder.rebuildWithinDateLock(PERIOD_END))
                .isInstanceOf(IllegalStateException.class);

        verify(redisTemplate, never()).rename(any(String.class), org.mockito.ArgumentMatchers.eq("popular-menu:2026-07-18"));
    }

    /**
     * 재구축은 해당 날짜의 투영과 같은 날짜 락 내부에서 실행되어 swap과 투영의 경합을 막는다.
     */
    @Test
    void rebuildRunsWhileTheSharedDateLockIsHeld() {
        RedisScenario scenario = new RedisScenario();
        AtomicBoolean lockHeld = new AtomicBoolean();
        PopularMenuDateLockManager lockManager = mock(PopularMenuDateLockManager.class);
        doAnswer(invocation -> {
            lockHeld.set(true);
            try {
                return ((Supplier<Object>) invocation.getArgument(1)).get();
            } finally {
                lockHeld.set(false);
            }
        }).when(lockManager).withDateLock(any(LocalDate.class), any(Supplier.class));
        PopularMenuCacheRebuilder rebuilder = mock(PopularMenuCacheRebuilder.class);
        doAnswer(invocation -> {
            assertThat(lockHeld).isTrue();
            scenario.put(invocation.getArgument(0), Map.of());
            return null;
        }).when(rebuilder).rebuildWithinDateLock(any(LocalDate.class));
        MenuRepository menuRepository = mock(MenuRepository.class);
        PopularMenuService service = new PopularMenuService(
                scenario.redisTemplate,
                menuRepository,
                mock(CoffeeOrderRepository.class),
                mock(PopularMenuCacheUpdater.class),
                rebuilder,
                lockManager,
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), BUSINESS_ZONE)
        );

        service.getPopularMenus();

        verify(rebuilder, org.mockito.Mockito.times(7)).rebuildWithinDateLock(any(LocalDate.class));
    }

    /**
     * Redis 날짜 락을 획득하지 못해도 MySQL의 완료 주문으로 직전 7일 응답을 정확히 만든다.
     */
    @Test
    void fallsBackToCommittedMySqlOrdersWhenRedisDateLockFails() {
        RedisScenario scenario = new RedisScenario();
        PopularMenuDateLockManager lockManager = mock(PopularMenuDateLockManager.class);
        doThrow(new IllegalStateException("Redis 날짜 락 실패"))
                .when(lockManager)
                .withDateLock(any(LocalDate.class), any(Supplier.class));
        CoffeeOrderRepository coffeeOrderRepository = mock(CoffeeOrderRepository.class);
        java.util.List<MenuOrderCount> orderCounts = java.util.List.of(
                menuOrderCount(3L, 4L),
                menuOrderCount(1L, 4L),
                menuOrderCount(2L, 2L)
        );
        when(coffeeOrderRepository.aggregateMenuCounts(
                Instant.parse("2026-07-11T15:00:00Z"),
                Instant.parse("2026-07-18T15:00:00Z")
        )).thenReturn(orderCounts);
        MenuRepository menuRepository = mock(MenuRepository.class);
        when(menuRepository.findById(anyLong())).thenAnswer(invocation -> Optional.of(menu((Long) invocation.getArgument(0))));
        PopularMenuCacheUpdater updater = mock(PopularMenuCacheUpdater.class);
        PopularMenuCacheRebuilder rebuilder = mock(PopularMenuCacheRebuilder.class);
        PopularMenuService service = new PopularMenuService(
                scenario.redisTemplate,
                menuRepository,
                coffeeOrderRepository,
                updater,
                rebuilder,
                lockManager,
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), BUSINESS_ZONE)
        );

        PopularMenuListResponse response = service.getPopularMenus();

        assertThat(response.periodStartDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(response.periodEndDate()).isEqualTo(PERIOD_END);
        assertThat(response.menus())
                .extracting(PopularMenuListResponse.PopularMenuResponse::menuId,
                        PopularMenuListResponse.PopularMenuResponse::orderCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 4L),
                        org.assertj.core.groups.Tuple.tuple(3L, 4L),
                        org.assertj.core.groups.Tuple.tuple(2L, 2L)
                );
        verifyNoInteractions(updater, rebuilder);
    }

    private PopularMenuService service(
            RedisScenario scenario,
            PopularMenuCacheUpdater updater,
            PopularMenuCacheRebuilder rebuilder
    ) {
        MenuRepository menuRepository = mock(MenuRepository.class);
        when(menuRepository.findById(anyLong())).thenAnswer(invocation -> Optional.of(menu((Long) invocation.getArgument(0))));
        return new PopularMenuService(
                scenario.redisTemplate,
                menuRepository,
                mock(CoffeeOrderRepository.class),
                updater,
                rebuilder,
                immediateLockManager(),
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), BUSINESS_ZONE)
        );
    }

    private Menu menu(long id) {
        Menu menu = mock(Menu.class);
        when(menu.getId()).thenReturn(id);
        when(menu.getName()).thenReturn("Menu " + id);
        when(menu.getPrice()).thenReturn(4_000L + id);
        return menu;
    }

    private MenuOrderCount menuOrderCount(long menuId, long orderCount) {
        MenuOrderCount menuOrderCount = mock(MenuOrderCount.class);
        when(menuOrderCount.getMenuId()).thenReturn(menuId);
        when(menuOrderCount.getOrderCount()).thenReturn(orderCount);
        return menuOrderCount;
    }

    @SuppressWarnings("unchecked")
    private PopularMenuDateLockManager immediateLockManager() {
        PopularMenuDateLockManager lockManager = mock(PopularMenuDateLockManager.class);
        doAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get())
                .when(lockManager)
                .withDateLock(any(LocalDate.class), any(Supplier.class));
        return lockManager;
    }

    @SuppressWarnings("unchecked")
    private static final class RedisScenario {

        private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        private final Map<String, Set<ZSetOperations.TypedTuple<String>>> values = new LinkedHashMap<>();

        private RedisScenario() {
            ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
            when(redisTemplate.hasKey(any(String.class))).thenAnswer(invocation -> values.containsKey(invocation.getArgument(0)));
            when(redisTemplate.type(any(String.class))).thenAnswer(invocation ->
                    values.containsKey(invocation.getArgument(0)) ? DataType.ZSET : DataType.NONE
            );
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.rangeWithScores(any(String.class), anyLong(), anyLong()))
                    .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        }

        private void put(LocalDate date, Map<Long, Long> counts) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(new DefaultTypedTuple<>("cache:ready", 0D));
            counts.forEach((menuId, count) -> tuples.add(
                    new DefaultTypedTuple<>("menu:" + menuId, count.doubleValue())
            ));
            values.put("popular-menu:" + date, tuples);
        }
    }
}
