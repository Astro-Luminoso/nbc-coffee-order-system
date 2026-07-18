package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.orderattempt.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecord;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyRecordId;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 주문 시도 생성 API의 HTTP 및 영속성 계약을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderAttemptControllerIntegrationTest {

    private static final String ORDER_ATTEMPT_ID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdMenuIds = new ArrayList<>();
    private final List<String> createdOrderAttemptIds = new ArrayList<>();

    /**
     * 주문 시도 생성은 대기 레코드만 저장하고 결제 관련 데이터를 변경하지 않는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void createOrderAttemptStoresPendingAttemptWithoutPaymentSideEffects() throws Exception {
        User user = saveUser(10_000L);
        Menu menu = saveMenu("Americano", 4_500L);
        long ordersBefore = tableRowCount("coffee_order");
        long orderItemsBefore = tableRowCount("order_item");
        long outboxTasksBefore = tableRowCount("order_outbox");
        Instant requestedAt = Instant.now();

        MvcResult result = mockMvc.perform(createRequest(user.getId(), menu.getId()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String orderAttemptId = responseStringField(responseBody, "orderAttemptId");
        OffsetDateTime expiresAt = OffsetDateTime.parse(responseStringField(responseBody, "expiresAt"));
        createdOrderAttemptIds.add(orderAttemptId);

        assertThat(orderAttemptId).matches(ORDER_ATTEMPT_ID_PATTERN);
        assertThat(expiresAt.toInstant()).isAfter(requestedAt);
        IdempotencyRecord record = idempotencyRecordRepository.findById(new IdempotencyRecordId(
                IdempotencyOperation.ORDER_ATTEMPT,
                orderAttemptId
        )).orElseThrow();
        assertThat(record.isCompleted()).isFalse();
        assertThat(record.getRequestBody()).isEqualTo("{\"userId\":" + user.getId() + ",\"menuId\":" + menu.getId() + "}");
        assertThat(record.getExpiresAt()).isAfter(requestedAt);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
        Menu persistedMenu = menuRepository.findById(menu.getId()).orElseThrow();
        assertThat(persistedMenu.getName()).isEqualTo("Americano");
        assertThat(persistedMenu.getPrice()).isEqualTo(4_500L);
        assertThat(tableRowCount("coffee_order")).isEqualTo(ordersBefore);
        assertThat(tableRowCount("order_item")).isEqualTo(orderItemsBefore);
        assertThat(tableRowCount("order_outbox")).isEqualTo(outboxTasksBefore);
    }

    /**
     * 필수 식별자가 없거나 양수가 아닌 요청을 처리하지 않고 거부하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void createOrderAttemptRejectsMissingAndNonPositiveIdentifiers() throws Exception {
        long recordsBefore = idempotencyRecordRepository.count();

        mockMvc.perform(post("/api/v1/order-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(createRequest(0L, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));
        mockMvc.perform(createRequest(1L, 0L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.httpStatus").value(400))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST"));

        assertThat(idempotencyRecordRepository.count()).isEqualTo(recordsBefore);
    }

    /**
     * 존재하지 않는 사용자의 주문 시도는 저장하지 않고 사용자 없음 응답을 반환하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void createOrderAttemptReturnsNotFoundWhenUserDoesNotExist() throws Exception {
        Menu menu = saveMenu("Cafe Latte", 5_000L);
        long recordsBefore = idempotencyRecordRepository.count();
        long ordersBefore = tableRowCount("coffee_order");

        mockMvc.perform(createRequest(9_999_999L, menu.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.data.code").value("USER_NOT_FOUND"));

        assertThat(idempotencyRecordRepository.count()).isEqualTo(recordsBefore);
        assertThat(menuRepository.findById(menu.getId())).isPresent();
        assertThat(tableRowCount("coffee_order")).isEqualTo(ordersBefore);
    }

    /**
     * 존재하지 않는 메뉴의 주문 시도는 저장하지 않고 메뉴 없음 응답을 반환하는지 검증한다.
     *
     * @throws Exception HTTP 요청 처리에 실패한 경우
     */
    @Test
    void createOrderAttemptReturnsNotFoundWhenMenuDoesNotExist() throws Exception {
        User user = saveUser(10_000L);
        long recordsBefore = idempotencyRecordRepository.count();
        long ordersBefore = tableRowCount("coffee_order");

        mockMvc.perform(createRequest(user.getId(), 9_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.httpStatus").value(404))
                .andExpect(jsonPath("$.data.code").value("MENU_NOT_FOUND"));

        assertThat(idempotencyRecordRepository.count()).isEqualTo(recordsBefore);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getBalance()).isEqualTo(10_000L);
        assertThat(tableRowCount("coffee_order")).isEqualTo(ordersBefore);
    }

    /**
     * 테스트에서 생성한 대기 주문 시도와 참조 데이터를 삭제한다.
     */
    @AfterEach
    void cleanUp() {
        createdOrderAttemptIds.forEach(orderAttemptId -> idempotencyRecordRepository.deleteById(
                new IdempotencyRecordId(IdempotencyOperation.ORDER_ATTEMPT, orderAttemptId)
        ));
        userRepository.deleteAllById(createdUserIds);
        menuRepository.deleteAllById(createdMenuIds);
    }

    /**
     * 테스트용 사용자를 저장한다.
     *
     * @param balance 초기 포인트 잔액
     * @return 저장된 사용자
     */
    private User saveUser(long balance) {
        User user = userRepository.saveAndFlush(new User(balance));
        createdUserIds.add(user.getId());
        return user;
    }

    /**
     * 테스트용 메뉴를 저장한다.
     *
     * @param name 메뉴명
     * @param price 메뉴 가격
     * @return 저장된 메뉴
     */
    private Menu saveMenu(String name, long price) {
        Menu menu = menuRepository.saveAndFlush(new Menu(name, price));
        createdMenuIds.add(menu.getId());
        return menu;
    }

    /**
     * 주문 시도 생성 HTTP 요청을 구성한다.
     *
     * @param userId 주문 사용자 식별자
     * @param menuId 주문 메뉴 식별자
     * @return 구성된 HTTP 요청
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(long userId, long menuId) {
        return post("/api/v1/order-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}");
    }

    /**
     * 지정한 주문 관련 테이블의 현재 행 수를 조회한다.
     *
     * @param tableName 조회할 테이블명
     * @return 테이블 행 수
     */
    private long tableRowCount(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject("select count(*) from " + tableName, Long.class);
            return count == null ? 0L : count;
        } catch (BadSqlGrammarException exception) {
            return 0L;
        }
    }

    /**
     * JSON 응답에서 문자열 필드를 추출한다.
     *
     * @param responseBody HTTP 응답 본문
     * @param fieldName 추출할 필드명
     * @return 필드의 문자열 값
     */
    private String responseStringField(String responseBody, String fieldName) {
        Pattern fieldPattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = fieldPattern.matcher(responseBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
