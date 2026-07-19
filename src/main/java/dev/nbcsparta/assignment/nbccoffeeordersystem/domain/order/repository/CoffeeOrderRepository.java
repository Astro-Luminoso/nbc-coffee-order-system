package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.PopularityProjectionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 완료 주문의 영속성 접근과 인기 메뉴 집계를 담당한다.
 */
public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {

    List<CoffeeOrder> findAllByCollectionStatus(CollectionStatus collectionStatus);

    /**
     * 전송 대기 상태인 주문을 배타 잠금하여 조회한다.
     *
     * @param orderId 조회할 주문 식별자
     * @param collectionStatus 조회할 수집 전송 상태
     * @return 잠긴 전송 대기 주문
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select coffeeOrder
            from CoffeeOrder coffeeOrder
            where coffeeOrder.id = :orderId
              and coffeeOrder.collectionStatus = :collectionStatus
            """)
    Optional<CoffeeOrder> findByIdAndCollectionStatusForUpdate(
            @Param("orderId") long orderId,
            @Param("collectionStatus") CollectionStatus collectionStatus
    );

    List<CoffeeOrder> findAllByPopularityProjectionStatusAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByIdAsc(
            PopularityProjectionStatus popularityProjectionStatus,
            Instant from,
            Instant to
    );

    @Query("""
            select coffeeOrder.menu.id as menuId, count(coffeeOrder) as orderCount
            from CoffeeOrder coffeeOrder
            where coffeeOrder.orderedAt >= :from
              and coffeeOrder.orderedAt < :to
              and coffeeOrder.popularityProjectionStatus = :popularityProjectionStatus
            group by coffeeOrder.menu.id
            """)
    List<MenuOrderCount> aggregateMenuCountsByPopularityProjectionStatus(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("popularityProjectionStatus") PopularityProjectionStatus popularityProjectionStatus
    );

    @Query("""
            select coffeeOrder.menu.id as menuId, count(coffeeOrder) as orderCount
            from CoffeeOrder coffeeOrder
            where coffeeOrder.orderedAt >= :from
              and coffeeOrder.orderedAt < :to
            group by coffeeOrder.menu.id
            """)
    List<MenuOrderCount> aggregateMenuCounts(@Param("from") Instant from, @Param("to") Instant to);
}
