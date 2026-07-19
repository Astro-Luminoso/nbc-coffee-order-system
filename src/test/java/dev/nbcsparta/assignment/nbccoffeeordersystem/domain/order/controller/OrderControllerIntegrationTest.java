package dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.entity.IdempotencyOperation;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.idempotency.repository.IdempotencyRecordRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.entity.Menu;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.menu.repository.MenuRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.entity.User;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.user.repository.UserRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector.DataCollectionClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 주문 결제 API의 원자성, 멱등성, 커밋 후 외부 전달을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CoffeeOrderRepository coffeeOrderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @MockitoBean
    private DataCollectionClient dataCollectionClient;

    @AfterEach
    void cleanUp() {
        coffeeOrderRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
        userRepository.deleteAll();
        menuRepository.deleteAll();
    }

    @Test
    void createsOnePaidOrderReplaysItAndDeliversOnlyAfterTheFirstCommit() throws Exception {
        User user = userRepository.saveAndFlush(new User(10_000L));
        Menu menu = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));

        mockMvc.perform(orderRequest("order-replay", user.getId(), menu.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.menu.id").value(menu.getId()))
                .andExpect(jsonPath("$.data.paymentAmount").value(4_500))
                .andExpect(jsonPath("$.data.remainingBalance").value(5_500));
        mockMvc.perform(orderRequest("order-replay", user.getId(), menu.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingBalance").value(5_500));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(5_500L);
        assertThat(coffeeOrderRepository.count()).isEqualTo(1L);
        assertThat(idempotencyRecordRepository.findAll())
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.getId().getOperation()).isEqualTo(IdempotencyOperation.ORDER_PAYMENT);
                    assertThat(record.isCompleted()).isTrue();
                    assertThat(record.getOrderId()).isNotNull();
                });
        verify(dataCollectionClient, times(1)).collect(user.getId(), menu.getId(), 4_500L);
    }

    @Test
    void rejectsInsufficientPointsWithoutPersistingAnOrderOrDeliveringData() throws Exception {
        User user = userRepository.saveAndFlush(new User(4_499L));
        Menu menu = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));

        mockMvc.perform(orderRequest("insufficient", user.getId(), menu.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("INSUFFICIENT_POINTS"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(4_499L);
        assertThat(coffeeOrderRepository.count()).isZero();
        verifyNoInteractions(dataCollectionClient);
    }

    @Test
    void rejectsReusingAnOrderPaymentKeyForADifferentRequest() throws Exception {
        User user = userRepository.saveAndFlush(new User(20_000L));
        Menu americano = menuRepository.saveAndFlush(new Menu("Americano", 4_500L));
        Menu latte = menuRepository.saveAndFlush(new Menu("Cafe Latte", 5_000L));

        mockMvc.perform(orderRequest("reused-order-key", user.getId(), americano.getId()))
                .andExpect(status().isCreated());
        mockMvc.perform(orderRequest("reused-order-key", user.getId(), latte.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(coffeeOrderRepository.count()).isEqualTo(1L);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPointBalance()).isEqualTo(15_500L);
        verify(dataCollectionClient, times(1)).collect(user.getId(), americano.getId(), 4_500L);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder orderRequest(
            String key,
            long userId,
            long menuId
    ) {
        return post("/api/v1/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + userId + ",\"menuId\":" + menuId + "}");
    }
}
