package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.controller;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.service.MenuService;

/**
 * 메뉴 목록 조회 API의 HTTP 계약을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class MenuControllerIntegrationTest {

    private MockMvc mockMvc;

    @Mock
    private MenuService menuService;

    /**
     * 각 테스트 전에 독립된 메뉴 컨트롤러 테스트 환경을 구성한다.
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MenuController(menuService)).build();
    }

    /**
     * 메뉴를 식별자 오름차순으로 필요한 필드만 반환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void getMenusReturnsOnlyRequiredFieldsInAscendingIdOrder() throws Exception {
        Menu americano = mock(Menu.class);
        when(americano.getId()).thenReturn(1L);
        when(americano.getName()).thenReturn("Americano");
        when(americano.getPrice()).thenReturn(4500L);
        Menu cafeLatte = mock(Menu.class);
        when(cafeLatte.getId()).thenReturn(2L);
        when(cafeLatte.getName()).thenReturn("Cafe Latte");
        when(cafeLatte.getPrice()).thenReturn(5000L);
        Menu espresso = mock(Menu.class);
        when(espresso.getId()).thenReturn(3L);
        when(espresso.getName()).thenReturn("Espresso");
        when(espresso.getPrice()).thenReturn(3500L);
        when(menuService.getMenus()).thenReturn(List.of(americano, cafeLatte, espresso));

        mockMvc.perform(get("/api/v1/menus").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.menus", hasSize(3)))
                .andExpect(jsonPath("$.data.menus[0]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.menus[0].id").value(1))
                .andExpect(jsonPath("$.data.menus[0].name").value("Americano"))
                .andExpect(jsonPath("$.data.menus[0].price").value(4500))
                .andExpect(jsonPath("$.data.menus[1]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.menus[1].id").value(2))
                .andExpect(jsonPath("$.data.menus[1].name").value("Cafe Latte"))
                .andExpect(jsonPath("$.data.menus[1].price").value(5000))
                .andExpect(jsonPath("$.data.menus[2]", aMapWithSize(3)))
                .andExpect(jsonPath("$.data.menus[2].id").value(3))
                .andExpect(jsonPath("$.data.menus[2].name").value("Espresso"))
                .andExpect(jsonPath("$.data.menus[2].price").value(3500));
    }

    /**
     * 메뉴가 없을 때 빈 메뉴 배열을 반환하는지 검증한다.
     *
     * @throws Exception MockMvc 요청 처리에 실패한 경우
     */
    @Test
    void getMenusReturnsEmptyArrayWhenNoMenuExists() throws Exception {
        when(menuService.getMenus()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/menus").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.menus", empty()));
    }
}
