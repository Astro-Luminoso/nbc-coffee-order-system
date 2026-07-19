package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector.DataCollectionClient;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동일 주문의 동시 수집 전송이 한 번만 외부 플랫폼을 호출하는지 검증한다.
 */
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCollectionDeliveryConcurrencyIntegrationTest {

    private static final long WAIT_SECONDS = 5L;

    @Autowired
    private OrderCollectionDeliveryService orderCollectionDeliveryService;

    @Autowired
    private CoffeeOrderRepository coffeeOrderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @MockitoBean
    private DataCollectionClient dataCollectionClient;

    @AfterEach
    void cleanUp() {
        coffeeOrderRepository.deleteAll();
        userRepository.deleteAll();
        menuRepository.deleteAll();
    }

    /**
     * 첫 전송이 DB 잠금을 보유한 동안 두 번째 전송을 시작해도 외부 호출은 한 번만 수행하는지 검증한다.
     *
     * @throws Exception 병렬 작업 또는 제한 시간 대기에 실패한 경우
     */
    @Test
    void concurrentDeliveryAttemptsCollectOnlyOnceAndMarkOrderSucceeded() throws Exception {
        User user = userRepository.saveAndFlush(new User(10_000L));
        Menu menu = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));
        CoffeeOrder order = coffeeOrderRepository.saveAndFlush(new CoffeeOrder(user, menu, 4_500L, Instant.now()));

        CountDownLatch firstCollectionStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCollectionToFinish = new CountDownLatch(1);
        CountDownLatch secondDeliveryAttemptStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstCollectionStarted.countDown();
            assertThat(allowFirstCollectionToFinish.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(dataCollectionClient).collect(order.getId(), user.getId(), menu.getId(), 4_500L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstDelivery = executor.submit(() -> orderCollectionDeliveryService.deliver(order.getId()));
            assertThat(firstCollectionStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> secondDelivery = executor.submit(() -> {
                secondDeliveryAttemptStarted.countDown();
                orderCollectionDeliveryService.deliver(order.getId());
            });
            assertThat(secondDeliveryAttemptStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            allowFirstCollectionToFinish.countDown();
            firstDelivery.get(WAIT_SECONDS, TimeUnit.SECONDS);
            secondDelivery.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertThat(coffeeOrderRepository.findById(order.getId()).orElseThrow().getCollectionStatus())
                    .isEqualTo(CollectionStatus.SUCCEEDED);
            verify(dataCollectionClient, times(1)).collect(order.getId(), user.getId(), menu.getId(), 4_500L);
        } finally {
            allowFirstCollectionToFinish.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }
}
