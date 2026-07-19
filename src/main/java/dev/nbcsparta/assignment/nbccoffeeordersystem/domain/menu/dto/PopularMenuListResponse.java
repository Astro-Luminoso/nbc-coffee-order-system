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
    public record PopularMenuResponse(Long menuId, String name, Long price, Long orderCount) {
        public static PopularMenuResponse of(Menu menu, long orderCount) {
            return new PopularMenuResponse(menu.getId(), menu.getName(), menu.getPrice(), orderCount);
        }
    }
}
