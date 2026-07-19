package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.controller;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.dto.PopularMenuListResponse;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.PopularMenuService;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.dto.CommonApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인기 메뉴 HTTP 요청을 처리한다.
 */
@RestController
@RequestMapping("/api/v1/menus/popular")
public class PopularMenuController {

    private final PopularMenuService popularMenuService;

    /**
     * 인기 메뉴 유스케이스를 사용하는 컨트롤러를 생성한다.
     *
     * @param popularMenuService 인기 메뉴 조회 서비스
     */
    public PopularMenuController(PopularMenuService popularMenuService) {
        this.popularMenuService = popularMenuService;
    }

    /**
     * 직전 완료 7일의 인기 메뉴 최대 세 건을 조회한다.
     *
     * @return 인기 메뉴 목록 공통 응답
     */
    @GetMapping
    public ResponseEntity<CommonApiResponse<PopularMenuListResponse>> getPopularMenus() {
        return ResponseEntity.ok(CommonApiResponse.of(HttpStatus.OK.value(), popularMenuService.getPopularMenus()));
    }
}
