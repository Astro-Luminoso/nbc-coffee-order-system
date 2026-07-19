package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.global.exception.MenuNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 메뉴 조회 기능을 제공하는 서비스이다.
 */
@Service
public class MenuService {

    private final MenuRepository menuRepository;

    /**
     * 메뉴 저장소를 사용하는 서비스를 생성한다.
     *
     * @param menuRepository 메뉴 저장소
     */
    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * 메뉴를 읽기 전용으로 조회한다.
     *
     * @param menuId 조회할 메뉴 식별자
     * @return 조회된 메뉴
     * @throws MenuNotFoundException 메뉴가 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public Menu getMenu(long menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new MenuNotFoundException(menuId));
    }

    /**
     * 모든 메뉴를 식별자 오름차순으로 조회한다.
     *
     * @return 식별자 오름차순으로 정렬된 메뉴 목록
     */
    @Transactional(readOnly = true)
    public List<Menu> getMenus() {
        return menuRepository.findAllByOrderByIdAsc();
    }
}
