package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.controller;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.MenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.MenuService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메뉴 조회 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    /**
     * 메뉴 조회 컨트롤러를 생성한다.
     *
     * @param menuService 메뉴 조회 서비스
     */
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * 등록된 모든 메뉴를 식별자 오름차순으로 조회한다.
     *
     * @return 메뉴 목록 공통 응답
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<MenuListResponse>> getMenus() {
        MenuListResponse response = MenuListResponse.from(menuService.getMenus());

        return ResponseEntity.ok(
                CommonApiResponse.of(HttpStatus.OK.value(), response)
        );
    }
}
