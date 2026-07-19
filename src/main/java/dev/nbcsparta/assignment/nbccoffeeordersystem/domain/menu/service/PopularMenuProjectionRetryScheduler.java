package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.PopularityProjectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 반영에 실패해 남은 인기 메뉴 투영을 주기적으로 재시도한다.
 */
@Component
public class PopularMenuProjectionRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PopularMenuProjectionRetryScheduler.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final CoffeeOrderRepository coffeeOrderRepository;
    private final PopularMenuCacheUpdater popularMenuCacheUpdater;

    /**
     * PENDING 투영 조회와 재시도 처리를 위한 의존성을 주입한다.
     *
     * @param coffeeOrderRepository 주문 투영 상태 저장소
     * @param popularMenuCacheUpdater Redis 투영 처리기
     */
    public PopularMenuProjectionRetryScheduler(
            CoffeeOrderRepository coffeeOrderRepository,
            PopularMenuCacheUpdater popularMenuCacheUpdater
    ) {
        this.coffeeOrderRepository = coffeeOrderRepository;
        this.popularMenuCacheUpdater = popularMenuCacheUpdater;
    }

    /**
     * 기본 1분 간격으로 모든 영업일의 PENDING 투영을 재시도한다.
     */
    /**
     * 모든 영업일의 PENDING 주문을 Redis 인기 메뉴 ZSET에 재투영한다.
     */
    @Scheduled(
            fixedDelayString = "${popular-menu.projection.retry-delay:60000}",
            initialDelayString = "${popular-menu.projection.initial-retry-delay:60000}"
    )
    public void retryPendingProjections() {
        Set<LocalDate> pendingDates = coffeeOrderRepository
                .findAllByPopularityProjectionStatusAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByIdAsc(
                        PopularityProjectionStatus.PENDING, Instant.EPOCH, Instant.now()
                )
                .stream()
                .map(order -> order.getOrderedAt().atZone(BUSINESS_ZONE).toLocalDate())
                .collect(Collectors.toSet());

        pendingDates.forEach(this::retryDate);
    }

    private void retryDate(LocalDate date) {
        try {
            popularMenuCacheUpdater.projectPendingForDate(date);
        } catch (RuntimeException exception) {
            log.error("인기 메뉴 Redis 투영 재시도에 실패했습니다. 날짜={}", date, exception);
        }
    }
}
