package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 일별 키가 없을 때 MySQL 주문 집계로 인기 메뉴를 복구하는지 검증한다.
 */
@DataJpaTest
class PopularMenuServiceIntegrationTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CoffeeOrderRepository coffeeOrderRepository;

    @Test
    void rebuildsMissingRedisDaysFromPaidOrdersAndUsesMenuIdAsTieBreaker() {
        User user = userRepository.saveAndFlush(new User(100_000L));
        Menu americano = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));
        Menu latte = menuRepository.saveAndFlush(new Menu("Cafe Latte", 5_000L));
        Menu mocha = menuRepository.saveAndFlush(new Menu("Cafe Mocha", 5_500L));
        Instant completedDate = LocalDate.of(2026, 7, 18).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant();
        Instant currentDate = LocalDate.of(2026, 7, 19).atTime(12, 0).atZone(BUSINESS_ZONE).toInstant();
        coffeeOrderRepository.save(new CoffeeOrder(user, latte, 5_000L, completedDate));
        coffeeOrderRepository.save(new CoffeeOrder(user, latte, 5_000L, completedDate));
        coffeeOrderRepository.save(new CoffeeOrder(user, americano, 4_500L, completedDate));
        coffeeOrderRepository.save(new CoffeeOrder(user, mocha, 5_500L, completedDate));
        coffeeOrderRepository.saveAndFlush(new CoffeeOrder(user, mocha, 5_500L, currentDate));

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        PopularMenuService service = new PopularMenuService(
                redisTemplate,
                coffeeOrderRepository,
                menuRepository,
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), BUSINESS_ZONE)
        );

        PopularMenuListResponse response = service.getPopularMenus();

        assertThat(response.periodStartDate()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(response.periodEndDate()).isEqualTo(LocalDate.of(2026, 7, 18));
        assertThat(response.menus())
                .extracting(PopularMenuListResponse.PopularMenuResponse::menuId,
                        PopularMenuListResponse.PopularMenuResponse::orderCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(latte.getId(), 2L),
                        org.assertj.core.groups.Tuple.tuple(americano.getId(), 1L),
                        org.assertj.core.groups.Tuple.tuple(mocha.getId(), 1L)
                );
    }
}
