package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import java.util.List;

/**
 * 메뉴 목록 응답을 표현한다.
 *
 * @param menus 메뉴 응답 목록
 */
public record MenuListResponse(
        List<MenuResponse> menus
) {

    /**
     * 메뉴 목록을 API 응답으로 변환한다.
     *
     * @param menus 변환할 메뉴 목록
     * @return 생성된 메뉴 목록 응답
     */
    public static MenuListResponse from(List<Menu> menus) {
        return new MenuListResponse(
                menus.stream()
                        .map(MenuResponse::from)
                        .toList()
        );
    }

    /**
     * 단일 메뉴 응답을 표현한다.
     *
     * @param id 메뉴 식별자
     * @param name 메뉴 이름
     * @param price 메뉴 가격
     */
    public record MenuResponse(
            Long id,
            String name,
            Long price
    ) {

        /**
         * 메뉴를 API 응답으로 변환한다.
         *
         * @param menu 변환할 메뉴
         * @return 생성된 단일 메뉴 응답
         */
        public static MenuResponse from(Menu menu) {
            return new MenuResponse(
                    menu.getId(),
                    menu.getName(),
                    menu.getPrice()
            );
        }
    }
}
