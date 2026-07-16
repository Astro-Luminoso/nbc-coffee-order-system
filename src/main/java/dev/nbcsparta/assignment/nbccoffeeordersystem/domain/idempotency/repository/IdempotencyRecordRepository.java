package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecord;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecordId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 멱등성 레코드의 영속성 접근을 담당한다.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {

    /**
     * 작업과 키로 레코드를 배타 잠금하여 조회한다.
     *
     * @param operation 조회할 작업 종류
     * @param idempotencyKey 조회할 멱등성 키
     * @return 잠긴 멱등성 레코드
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from IdempotencyRecord record
            where record.id.operation = :operation
              and record.id.idempotencyKey = :idempotencyKey
            """)
    Optional<IdempotencyRecord> findByOperationAndIdempotencyKeyForUpdate(
            @Param("operation") IdempotencyOperation operation,
            @Param("idempotencyKey") String idempotencyKey
    );
}
