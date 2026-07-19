package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 완료 주문의 영속성 접근과 인기 메뉴 집계를 담당한다.
 */
public interface CoffeeOrderRepository extends JpaRepository<CoffeeOrder, Long> {

    List<CoffeeOrder> findAllByCollectionStatus(CollectionStatus collectionStatus);

    @Query("""
            select coffeeOrder.menu.id as menuId, count(coffeeOrder) as orderCount
            from CoffeeOrder coffeeOrder
            where coffeeOrder.orderedAt >= :from
              and coffeeOrder.orderedAt < :to
            group by coffeeOrder.menu.id
            """)
    List<MenuOrderCount> aggregateMenuCounts(@Param("from") Instant from, @Param("to") Instant to);
}
