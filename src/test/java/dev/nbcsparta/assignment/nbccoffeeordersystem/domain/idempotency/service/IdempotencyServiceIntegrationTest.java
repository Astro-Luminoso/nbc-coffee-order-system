package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecord;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecordId;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.IdempotencyKeyReusedException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공유 데이터베이스 기반 포인트 충전 멱등성 동작을 검증한다.
 */
@DataJpaTest
@Import(IdempotencyService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyServiceIntegrationTest {

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 같은 키와 같은 요청은 최초 결과를 그대로 재사용하는지 검증한다.
     */
    @Test
    void reusesStoredCompletedResultForSameKeyAndRequest() {
        PreparedPointCharge first = inTransaction(() -> {
            PreparedPointCharge prepared = idempotencyService.preparePointCharge("charge-key-1", 1L, 10_000L);
            idempotencyService.completePointCharge(prepared, 200, "{\"httpStatus\":200,\"data\":{\"balance\":10000}}");
            return prepared;
        });

        PreparedPointCharge replay = inTransaction(
                () -> idempotencyService.preparePointCharge("charge-key-1", 1L, 10_000L)
        );

        assertThat(first.completed()).isFalse();
        assertThat(replay.completed()).isTrue();
        assertThat(replay.httpStatus()).isEqualTo(200);
        assertThat(replay.responseBody()).isEqualTo("{\"httpStatus\":200,\"data\":{\"balance\":10000}}");
    }

    /**
     * 다른 정규 요청에 같은 키를 사용하면 충돌 예외가 발생하는지 검증한다.
     */
    @Test
    void rejectsReusingKeyForDifferentCanonicalRequest() {
        inTransaction(() -> {
            PreparedPointCharge prepared = idempotencyService.preparePointCharge("charge-key-2", 1L, 10_000L);
            idempotencyService.completePointCharge(prepared, 200, "{\"httpStatus\":200}");
            return null;
        });

        assertThatThrownBy(() -> inTransaction(
                () -> idempotencyService.preparePointCharge("charge-key-2", 1L, 20_000L)
        )).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    /**
     * 정규 요청 해시와 완료 결과 보존 필드를 검증한다.
     */
    @Test
    void storesLowercaseSha256AndRetainsCompletedResultForAtLeastTwentyFourHours() {
        inTransaction(() -> {
            PreparedPointCharge prepared = idempotencyService.preparePointCharge("charge-key-3", 7L, 1_500L);
            idempotencyService.completePointCharge(prepared, 200, "{\"httpStatus\":200}");
            return null;
        });

        IdempotencyRecord record = idempotencyRecordRepository.findById(
                        new IdempotencyRecordId(IdempotencyOperation.POINT_CHARGE, "charge-key-3")
                )
                .orElseThrow();

        assertThat(record.getRequestBody()).isEqualTo("userId=7&amount=1500");
        assertThat(record.getRequestHash()).matches("[0-9a-f]{64}");
        assertThat(record.getHttpStatus()).isEqualTo(200);
        assertThat(record.getResponseBody()).isEqualTo("{\"httpStatus\":200}");
        assertThat(record.getCompletedAt()).isNotNull();
        assertThat(record.getExpiresAt()).isAfterOrEqualTo(record.getCompletedAt().plus(Duration.ofHours(24)));
    }

    /**
     * 같은 키의 첫 경쟁 요청이 하나의 완료 결과만 만들고 나머지는 이를 재사용하는지 검증한다.
     *
     * @throws Exception 비동기 작업 실행 또는 대기 중 오류가 발생한 경우
     */
    @Test
    void concurrentFirstRequestsReuseOneCompletedResult() throws Exception {
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try {
            List<Future<PreparedPointCharge>> futures = List.of(
                    executorService.submit(competingCharge(startBarrier)),
                    executorService.submit(competingCharge(startBarrier))
            );

            List<PreparedPointCharge> results = futures.stream()
                    .map(this::await)
                    .toList();

            assertThat(results).filteredOn(PreparedPointCharge::completed).hasSize(1);
            assertThat(results).filteredOn(result -> !result.completed()).hasSize(1);

            PreparedPointCharge replay = results.stream()
                    .filter(PreparedPointCharge::completed)
                    .findFirst()
                    .orElseThrow();
            assertThat(replay.httpStatus()).isEqualTo(200);
            assertThat(replay.responseBody()).isEqualTo("{\"httpStatus\":200,\"data\":{\"balance\":10000}}");

            IdempotencyRecord record = idempotencyRecordRepository.findById(
                            new IdempotencyRecordId(IdempotencyOperation.POINT_CHARGE, "concurrent-charge-key")
                    )
                    .orElseThrow();
            assertThat(record.isCompleted()).isTrue();
            assertThat(record.getResponseBody()).isEqualTo(replay.responseBody());
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * 동일 멱등성 키로 경쟁하는 하나의 포인트 충전 작업을 생성한다.
     *
     * @param startBarrier 두 작업의 시작을 맞출 동기화 장치
     * @return 트랜잭션 안에서 실행할 작업
     */
    private Callable<PreparedPointCharge> competingCharge(CyclicBarrier startBarrier) {
        return () -> inTransaction(() -> {
            startBarrier.await(5, TimeUnit.SECONDS);
            PreparedPointCharge prepared = idempotencyService.preparePointCharge("concurrent-charge-key", 1L, 10_000L);
            if (!prepared.completed()) {
                idempotencyService.completePointCharge(
                        prepared,
                        200,
                        "{\"httpStatus\":200,\"data\":{\"balance\":10000}}"
                );
            }
            return prepared;
        });
    }

    /**
     * 미래 결과를 제한 시간 안에 반환한다.
     *
     * @param future 대기할 미래 결과
     * @return 작업 결과
     */
    private PreparedPointCharge await(Future<PreparedPointCharge> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("동시 멱등성 검증 작업이 실패했습니다.", exception);
        }
    }

    /**
     * 지정한 작업을 새 트랜잭션에서 실행한다.
     *
     * @param callback 트랜잭션 안에서 실행할 작업
     * @param <T> 작업 반환 타입
     * @return 작업 반환값
     */
    private <T> T inTransaction(Callable<T> callback) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate.execute(ignored -> {
            try {
                return callback.call();
            } catch (Exception exception) {
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("멱등성 트랜잭션 작업을 실행할 수 없습니다.", exception);
            }
        });
    }
}
