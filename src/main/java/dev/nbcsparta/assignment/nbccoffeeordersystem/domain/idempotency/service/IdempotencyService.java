package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecord;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecordId;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.IdempotencyKeyReusedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 충전의 공유 DB 기반 멱등성 상태를 관리하는 서비스이다.
 */
@Service
public class IdempotencyService {

    private static final Duration POINT_CHARGE_PENDING_RETENTION = Duration.ofHours(24);

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final Duration orderAttemptTtl;

    /**
     * 멱등성 레코드 저장소를 사용하는 서비스를 생성한다.
     *
     * @param idempotencyRecordRepository 멱등성 레코드 저장소
     * @param orderAttemptTtl 주문 시도의 대기 상태 유지 시간
     */
    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            @Value("${order-attempt.ttl:PT30M}") Duration orderAttemptTtl
    ) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        if (orderAttemptTtl.isZero() || orderAttemptTtl.isNegative()) {
            throw new IllegalArgumentException("주문 시도 유지 시간은 양수여야 합니다.");
        }
        this.orderAttemptTtl = orderAttemptTtl;
    }

    /**
     * 서버가 발급한 식별자로 대기 상태 주문 시도를 저장한다.
     *
     * <p>저장되는 정규 요청은 사용자와 메뉴 식별자만 포함하며, 생성 이후 변경되지 않는다.
     * 이 단계에서는 포인트, 주문, 주문 항목 또는 배달 작업을 만들지 않는다.</p>
     *
     * @param userId 주문과 결제를 수행할 사용자 식별자
     * @param menuId 주문할 메뉴 식별자
     * @return 서버가 발급한 주문 시도 식별자와 만료 시각
     */
    @Transactional
    public CreatedOrderAttempt createOrderAttempt(long userId, long menuId) {
        String orderAttemptId = UUID.randomUUID().toString();
        String canonicalRequest = canonicalOrderAttemptRequest(userId, menuId);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(orderAttemptTtl);

        idempotencyRecordRepository.save(new IdempotencyRecord(
                new IdempotencyRecordId(IdempotencyOperation.ORDER_ATTEMPT, orderAttemptId),
                canonicalRequest,
                sha256(canonicalRequest),
                now,
                expiresAt
        ));

        return new CreatedOrderAttempt(orderAttemptId, expiresAt);
    }

    /**
     * 포인트 충전 멱등성 레코드를 독립 트랜잭션으로 먼저 예약한다.
     *
     * <p>같은 키를 동시에 예약하려는 경우 복합 기본 키 제약이 하나의 삽입만 허용한다.
     * 이미 존재하는 키는 이후 비즈니스 트랜잭션에서 잠금 조회하여 처리한다.</p>
     *
     * @param idempotencyKey 클라이언트 멱등성 키
     * @param userId 충전 대상 사용자 식별자
     * @param amount 충전 금액
     * @throws DataIntegrityViolationException 같은 키의 동시 삽입 경쟁에서 패배한 경우
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reservePointCharge(String idempotencyKey, long userId, long amount) {
        String canonicalRequest = canonicalPointChargeRequest(userId, amount);
        IdempotencyRecordId recordId = new IdempotencyRecordId(
                IdempotencyOperation.POINT_CHARGE,
                idempotencyKey
        );
        if (idempotencyRecordRepository.existsById(recordId)) {
            return;
        }

        Instant now = Instant.now();
        idempotencyRecordRepository.saveAndFlush(new IdempotencyRecord(
                recordId,
                canonicalRequest,
                sha256(canonicalRequest),
                now,
                now.plus(POINT_CHARGE_PENDING_RETENTION)
        ));
    }

    /**
     * 포인트 충전 멱등성 레코드가 이미 예약되었는지 확인한다.
     *
     * <p>동일 키의 삽입 경쟁으로 보이는 예외가 발생한 뒤, 실제로 다른 요청이
     * 레코드를 먼저 커밋했는지 확인하는 용도로 사용한다.</p>
     *
     * @param idempotencyKey 확인할 클라이언트 멱등성 키
     * @return 포인트 충전 멱등성 레코드가 존재하면 {@code true}
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean existsPointCharge(String idempotencyKey) {
        return idempotencyRecordRepository.existsById(new IdempotencyRecordId(
                IdempotencyOperation.POINT_CHARGE,
                idempotencyKey
        ));
    }

    /**
     * 예약된 포인트 충전 레코드를 잠근 뒤 새 실행 또는 저장된 완료 결과를 반환한다.
     *
     * @param idempotencyKey 클라이언트 멱등성 키
     * @param userId 충전 대상 사용자 식별자
     * @param amount 충전 금액
     * @return 새 실행 준비 결과 또는 저장된 완료 결과
     * @throws IdempotencyKeyReusedException 동일 키에 다른 요청을 사용한 경우
     */
    @Transactional
    public PreparedPointCharge preparePointCharge(String idempotencyKey, long userId, long amount) {
        String requestHash = sha256(canonicalPointChargeRequest(userId, amount));
        IdempotencyRecord snapshot = idempotencyRecordRepository.findByOperationAndIdempotencyKeyForUpdate(
                        IdempotencyOperation.POINT_CHARGE,
                        idempotencyKey
                )
                .orElseThrow(() -> new IllegalStateException("예약된 멱등성 레코드를 찾을 수 없습니다."));
        if (!snapshot.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException("동일한 멱등성 키에 다른 요청을 사용할 수 없습니다.");
        }
        if (snapshot.isCompleted()) {
            return PreparedPointCharge.completed(
                    idempotencyKey,
                    snapshot.getHttpStatus(),
                    snapshot.getResponseBody()
            );
        }
        return PreparedPointCharge.pending(idempotencyKey);
    }

    /**
     * 포인트 충전과 함께 최초 성공 HTTP 결과를 완료 상태로 저장한다.
     *
     * <p>호출자는 사용자 잔액 변경과 이 메서드를 같은 트랜잭션에서 호출해야 한다.</p>
     *
     * @param prepared 준비 단계에서 반환된 결과
     * @param httpStatus 최초 성공 HTTP 상태
     * @param responseBody 최초 성공 공통 응답 JSON
     */
    @Transactional
    public void completePointCharge(PreparedPointCharge prepared, int httpStatus, String responseBody) {
        if (prepared.completed()) {
            throw new IllegalArgumentException("저장된 완료 결과는 다시 완료 처리할 수 없습니다.");
        }

        IdempotencyRecord record = idempotencyRecordRepository.findByOperationAndIdempotencyKeyForUpdate(
                        IdempotencyOperation.POINT_CHARGE,
                        prepared.idempotencyKey()
                )
                .orElseThrow(() -> new IllegalStateException("준비된 멱등성 레코드를 찾을 수 없습니다."));
        if (record.isCompleted()) {
            throw new IllegalStateException("이미 완료된 멱등성 작업입니다.");
        }
        record.completePointCharge(httpStatus, responseBody, Instant.now());
    }

    /**
     * 포인트 충전 요청을 불변 필드 순서의 JSON으로 정규화한다.
     *
     * @param userId 충전 대상 사용자 식별자
     * @param amount 충전 금액
     * @return 정규 요청 JSON
     */
    private String canonicalPointChargeRequest(long userId, long amount) {
        return "{\"userId\":" + userId + ",\"amount\":" + amount + "}";
    }

    /**
     * 주문 시도 요청을 불변 필드 순서의 JSON으로 정규화한다.
     *
     * @param userId 주문과 결제를 수행할 사용자 식별자
     * @param menuId 주문할 메뉴 식별자
     * @return 정규 요청 JSON
     */
    private String canonicalOrderAttemptRequest(long userId, long menuId) {
        return "{\"userId\":" + userId + ",\"menuId\":" + menuId + "}";
    }

    /**
     * 문자열의 SHA-256 값을 소문자 16진수로 계산한다.
     *
     * @param value 해시할 문자열
     * @return 64자 소문자 16진수 SHA-256 값
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
