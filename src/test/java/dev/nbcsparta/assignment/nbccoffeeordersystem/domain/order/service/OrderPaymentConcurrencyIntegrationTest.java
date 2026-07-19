package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.dto.OrderPaymentResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.InsufficientPointsException;
import dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector.DataCollectionClient;
import java.util.ArrayList;
import java.util.List;
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
 * 서로 다른 멱등성 키로 동시에 결제해도 사용자 잔액을 초과해 차감하지 않는지 검증한다.
 */
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderPaymentConcurrencyIntegrationTest {

    private static final int ATTEMPT_COUNT = 4;
    private static final int SUCCESSFUL_PAYMENT_COUNT = 2;
    private static final long INITIAL_BALANCE = 9_000L;
    private static final long MENU_PRICE = 4_500L;
    private static final long WAIT_SECONDS = 5L;
    private static final String IDEMPOTENCY_KEY_PREFIX = "concurrent-payment-";

    @Autowired
    private OrderPaymentFacade orderPaymentFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CoffeeOrderRepository coffeeOrderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @MockitoBean
    private DataCollectionClient dataCollectionClient;

    @AfterEach
    void cleanUp() {
        coffeeOrderRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
        menuRepository.deleteAll();
    }

    @Test
    void concurrentPaymentsWithDifferentKeysDoNotOverspendOrCreateOrdersForFailedPayments() throws Exception {
        User user = userRepository.saveAndFlush(new User(INITIAL_BALANCE));
        Menu menu = menuRepository.saveAndFlush(new Menu("Americano", MENU_PRICE));
        CountDownLatch requestsReady = new CountDownLatch(ATTEMPT_COUNT);
        CountDownLatch startRequests = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPT_COUNT);
        try {
            List<Future<PaymentAttempt>> attempts = new ArrayList<>();
            for (int index = 0; index < ATTEMPT_COUNT; index++) {
                String idempotencyKey = IDEMPOTENCY_KEY_PREFIX + index;
                attempts.add(executor.submit(() -> {
                    requestsReady.countDown();
                    if (!startRequests.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 결제 시작 신호를 받지 못했습니다.");
                    }
                    try {
                        return PaymentAttempt.succeeded(orderPaymentFacade.pay(idempotencyKey, user.getId(), menu.getId()));
                    } catch (InsufficientPointsException exception) {
                        return PaymentAttempt.failed();
                    }
                }));
            }

            assertThat(requestsReady.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            startRequests.countDown();

            List<PaymentAttempt> results = new ArrayList<>();
            for (Future<PaymentAttempt> attempt : attempts) {
                results.add(attempt.get(WAIT_SECONDS, TimeUnit.SECONDS));
            }

            List<OrderPaymentResponse> successfulPayments = results.stream()
                    .filter(PaymentAttempt::succeeded)
                    .map(PaymentAttempt::response)
                    .toList();

            assertThat(successfulPayments).hasSize(SUCCESSFUL_PAYMENT_COUNT);
            assertThat(results).filteredOn(attempt -> !attempt.succeeded())
                    .hasSize(ATTEMPT_COUNT - SUCCESSFUL_PAYMENT_COUNT);
            assertThat(successfulPayments)
                    .extracting(OrderPaymentResponse::remainingBalance)
                    .containsExactlyInAnyOrder(MENU_PRICE, 0L);
            assertThat(successfulPayments)
                    .extracting(OrderPaymentResponse::orderId)
                    .doesNotHaveDuplicates();
            assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isZero();
            assertThat(coffeeOrderRepository.count()).isEqualTo(SUCCESSFUL_PAYMENT_COUNT);
            assertThat(coffeeOrderRepository.findAll())
                    .allSatisfy(order -> assertThat(order.getPaymentAmount()).isEqualTo(MENU_PRICE));
            assertThat(idempotencyRecordRepository.findAll())
                    .filteredOn(record -> record.getId().getOperation() == IdempotencyOperation.ORDER_PAYMENT
                            && record.getId().getIdempotencyKey().startsWith(IDEMPOTENCY_KEY_PREFIX))
                    .satisfiesExactlyInAnyOrder(
                            record -> assertThat(record.isCompleted()).isTrue(),
                            record -> assertThat(record.isCompleted()).isTrue(),
                            record -> {
                                assertThat(record.isCompleted()).isFalse();
                                assertThat(record.getOrderId()).isNull();
                            },
                            record -> {
                                assertThat(record.isCompleted()).isFalse();
                                assertThat(record.getOrderId()).isNull();
                            }
                    );
            verify(dataCollectionClient, times(SUCCESSFUL_PAYMENT_COUNT))
                    .collect(anyLong(), eq(user.getId()), eq(menu.getId()), eq(MENU_PRICE));
        } finally {
            startRequests.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private record PaymentAttempt(boolean succeeded, OrderPaymentResponse response) {

        private static PaymentAttempt succeeded(OrderPaymentResponse response) {
            return new PaymentAttempt(true, response);
        }

        private static PaymentAttempt failed() {
            return new PaymentAttempt(false, null);
        }
    }
}
