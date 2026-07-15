package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 메뉴 엔티티의 영속성 접근을 담당한다.
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /**
     * 모든 메뉴를 식별자 오름차순으로 조회한다.
     *
     * @return 식별자 오름차순으로 정렬된 메뉴 목록
     */
    List<Menu> findAllByOrderByIdAsc();
}
