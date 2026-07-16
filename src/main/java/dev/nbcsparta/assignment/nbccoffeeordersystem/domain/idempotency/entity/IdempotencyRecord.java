package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 분산 인스턴스 사이에서 재시도를 안전하게 만드는 영속 멱등성 레코드이다.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    private static final Duration COMPLETED_RESULT_RETENTION = Duration.ofHours(24);

    @EmbeddedId
    private IdempotencyRecordId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", nullable = false, columnDefinition = "JSON")
    private String requestBody;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IdempotencyStatus status;


    @Column(name = "order_id", unique = true)
    private Long orderId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "JSON")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * JPA가 멱등성 레코드를 생성할 때 사용하는 생성자이다.
     */
    protected IdempotencyRecord() {
    }

    /**
     * 대기 상태의 포인트 충전 멱등성 레코드를 생성한다.
     *
     * @param id 작업-키 복합 식별자
     * @param requestBody 불변 정규 요청 JSON
     * @param requestHash 정규 요청의 SHA-256 해시
     * @param createdAt 생성 시각
     * @param expiresAt 대기 상태 만료 시각
     */
    public IdempotencyRecord(
            IdempotencyRecordId id,
            String requestBody,
            String requestHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.requestBody = Objects.requireNonNull(requestBody);
        this.requestHash = Objects.requireNonNull(requestHash);
        this.status = IdempotencyStatus.PENDING;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    /**
     * 저장된 요청 해시가 현재 요청 해시와 같은지 확인한다.
     *
     * @param candidateHash 비교할 SHA-256 해시
     * @return 해시가 같으면 {@code true}
     */
    public boolean hasRequestHash(String candidateHash) {
        return requestHash.equals(candidateHash);
    }

    /**
     * 완료 상태인지 반환한다.
     *
     * @return 완료 상태이면 {@code true}
     */
    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    /**
     * 포인트 충전 결과를 완료 상태로 저장한다.
     *
     * @param httpStatus 최초 성공 HTTP 상태
     * @param responseBody 최초 성공 공통 응답 JSON
     * @param completedAt 완료 시각
     */
    public void completePointCharge(int httpStatus, String responseBody, Instant completedAt) {
        if (id.getOperation() != IdempotencyOperation.POINT_CHARGE) {
            throw new IllegalStateException("포인트 충전 작업만 완료할 수 있습니다.");
        }
        if (isCompleted()) {
            throw new IllegalStateException("이미 완료된 멱등성 작업입니다.");
        }
        this.status = IdempotencyStatus.COMPLETED;
        this.httpStatus = httpStatus;
        this.responseBody = Objects.requireNonNull(responseBody);
        this.completedAt = Objects.requireNonNull(completedAt);
        Instant minimumExpiry = completedAt.plus(COMPLETED_RESULT_RETENTION);
        if (expiresAt.isBefore(minimumExpiry)) {
            expiresAt = minimumExpiry;
        }
    }

    /**
     * 복합 식별자를 반환한다.
     *
     * @return 복합 식별자
     */
    public IdempotencyRecordId getId() {
        return id;
    }

    /**
     * 정규 요청 JSON을 반환한다.
     *
     * @return 정규 요청 JSON
     */
    public String getRequestBody() {
        return requestBody;
    }

    /**
     * 정규 요청 SHA-256 해시를 반환한다.
     *
     * @return 64자 소문자 16진수 해시
     */
    public String getRequestHash() {
        return requestHash;
    }

    /**
     * 최초 성공 HTTP 상태를 반환한다.
     *
     * @return 최초 성공 HTTP 상태
     */
    public Integer getHttpStatus() {
        return httpStatus;
    }

    /**
     * 최초 성공 공통 응답 JSON을 반환한다.
     *
     * @return 최초 성공 공통 응답 JSON
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * 완료 시각을 반환한다.
     *
     * @return 완료 시각
     */
    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * 만료 시각을 반환한다.
     *
     * @return 만료 시각
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }
}
