package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import java.time.LocalDate;
import java.util.List;

/**
 * 서울 영업일 기준 직전 완료 7일의 인기 메뉴 응답을 표현한다.
 */
public record PopularMenuListResponse(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        List<PopularMenuResponse> menus
) {

    /**
     * 인기 메뉴 한 건의 응답 정보다.
     */
    public record PopularMenuResponse(Long menuId, String name, Long price, Long orderCount) {

        /**
         * 메뉴와 주문 횟수로 인기 메뉴 응답을 생성한다.
         *
         * @param menu 메뉴 엔티티
         * @param orderCount 집계된 주문 횟수
         * @return 인기 메뉴 응답
         */
        public static PopularMenuResponse of(Menu menu, long orderCount) {
            return new PopularMenuResponse(menu.getId(), menu.getName(), menu.getPrice(), orderCount);
        }
    }
}
