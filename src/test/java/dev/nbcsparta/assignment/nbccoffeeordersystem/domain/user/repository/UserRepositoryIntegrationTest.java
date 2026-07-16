package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/**
 * 사용자 저장소의 비관적 잠금 조회를 검증한다.
 */
@DataJpaTest
@Import(UserService.class)
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserService userService;

    /**
     * 사용자 행을 비관적 쓰기 잠금으로 조회하고 변경 사항을 반영하는지 검증한다.
     */
    @Test
    @Transactional
    void findByIdForUpdateReturnsLockedUserForBalanceUpdate() {
        User savedUser = userRepository.saveAndFlush(new User(100L));
        entityManager.clear();

        User lockedUser = userRepository.findByIdForUpdate(savedUser.getId()).orElseThrow();
        lockedUser.charge(50L);
        entityManager.flush();
        entityManager.clear();

        User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(updatedUser.getBalance()).isEqualTo(150L);
    }

    /**
     * 동시에 여러 번 충전해도 행 잠금으로 갱신 유실이 발생하지 않는지 검증한다.
     *
     * @throws Exception 병렬 충전 작업 실행에 실패한 경우
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentChargesDoNotLoseBalanceUpdates() throws Exception {
        User savedUser = userRepository.saveAndFlush(new User(0L));
        int requestCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return userService.charge(savedUser.getId(), 100L);
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Long> future : futures) {
                future.get();
            }

            User updatedUser = userRepository.findById(savedUser.getId()).orElseThrow();
            assertThat(updatedUser.getBalance()).isEqualTo(2_000L);
        } finally {
            executor.shutdownNow();
            userRepository.deleteById(savedUser.getId());
        }
    }
}
