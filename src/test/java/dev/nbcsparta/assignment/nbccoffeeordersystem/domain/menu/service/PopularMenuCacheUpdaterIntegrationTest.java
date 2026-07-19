package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.PopularityProjectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.event.OrderCompletedEvent;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Redis 인기 메뉴 투영의 실패 복구와 중복 안전성을 검증한다.
 */
@DataJpaTest
class PopularMenuCacheUpdaterIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CoffeeOrderRepository coffeeOrderRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 최초 Redis 반영 실패는 PENDING으로 남고, 재시도 성공 또는 기존 marker 확인 후에만 완료된다.
     */
    @Test
    void keepsProjectionPendingAfterRedisFailureAndCompletesItOnRetry() {
        Instant orderedAt = LocalDate.of(2026, 7, 18).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant();
        CoffeeOrder order = saveOrder(orderedAt);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        PopularMenuDateLockManager dateLockManager = immediateLockManager();
        PopularMenuCacheUpdater updater = new PopularMenuCacheUpdater(
                redisTemplate, coffeeOrderRepository, dateLockManager
        );

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Redis 연결 실패"));

        updater.project(eventOf(order));
        entityManager.flush();
        entityManager.clear();

        assertThat(coffeeOrderRepository.findById(order.getId()).orElseThrow().getPopularityProjectionStatus())
                .isEqualTo(PopularityProjectionStatus.PENDING);

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(0L);

        updater.projectPendingForDate(LocalDate.of(2026, 7, 18));
        entityManager.flush();
        entityManager.clear();

        assertThat(coffeeOrderRepository.findById(order.getId()).orElseThrow().getPopularityProjectionStatus())
                .isEqualTo(PopularityProjectionStatus.SUCCEEDED);
    }

    private CoffeeOrder saveOrder(Instant orderedAt) {
        User user = userRepository.saveAndFlush(new User(100_000L));
        Menu menu = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));
        return coffeeOrderRepository.saveAndFlush(new CoffeeOrder(user, menu, 4_500L, orderedAt));
    }

    private OrderCompletedEvent eventOf(CoffeeOrder order) {
        return new OrderCompletedEvent(
                order.getId(), order.getUserId(), order.getMenuId(), order.getPaymentAmount(), order.getOrderedAt()
        );
    }

    @SuppressWarnings("unchecked")
    private PopularMenuDateLockManager immediateLockManager() {
        PopularMenuDateLockManager lockManager = mock(PopularMenuDateLockManager.class);
        doAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get())
                .when(lockManager)
                .withDateLock(any(LocalDate.class), any(Supplier.class));
        return lockManager;
    }
}
