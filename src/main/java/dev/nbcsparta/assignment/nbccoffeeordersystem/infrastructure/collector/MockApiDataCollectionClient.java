package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 완료 주문을 MockAPI.io 주문 자원과 대조한 뒤 필요한 경우 생성한다.
 */
@Component
public class MockApiDataCollectionClient implements DataCollectionClient {

    private static final String ORDERS_PATH = "/orders";

    private final RestClient restClient;

    /**
     * MockAPI.io 전용 HTTP 클라이언트를 주입한다.
     *
     * @param restClient MockAPI.io 전용 HTTP 클라이언트
     */
    public MockApiDataCollectionClient(@Qualifier("mockApiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 주문 식별자로 기존 데이터를 먼저 조회하고, 조회 결과가 비어 있을 때만 생성한다.
     *
     * @param orderId 주문 식별자
     * @param userId 사용자 식별자
     * @param menuId 메뉴 식별자
     * @param paymentAmount 결제 금액
     */
    @Override
    public void collect(long orderId, long userId, long menuId, long paymentAmount) {
        MockApiOrderRequest request = new MockApiOrderRequest(orderId, userId, menuId, paymentAmount);
        MockApiOrderResponse[] lookupResponses = lookup(orderId);

        if (lookupResponses == null) {
            throw new MockApiDataCollectionException("외부 주문 조회 응답 본문이 없습니다.");
        }
        if (lookupResponses.length == 0) {
            create(request);
            return;
        }
        if (lookupResponses.length != 1) {
            throw new MockApiDataCollectionException("외부 주문 조회 결과가 한 건이 아닙니다.");
        }
        if (!matches(lookupResponses[0], request)) {
            throw new MockApiDataCollectionException("외부 주문 조회 데이터가 요청과 일치하지 않습니다.");
        }
    }

    private MockApiOrderResponse[] lookup(long orderId) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(ORDERS_PATH)
                            .queryParam("orderId", orderId)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            (request, response) -> {
                                throw new MockApiDataCollectionException(
                                        "외부 주문 조회 응답이 성공 상태가 아닙니다."
                                );
                            }
                    )
                    .body(MockApiOrderResponse[].class);
        } catch (MockApiDataCollectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MockApiDataCollectionException("외부 주문 조회 요청에 실패했습니다.");
        }
    }

    private void create(MockApiOrderRequest request) {
        MockApiOrderResponse createResponse;
        try {
            createResponse = restClient.post()
                    .uri(ORDERS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            (httpRequest, response) -> {
                                throw new MockApiDataCollectionException(
                                        "외부 주문 생성 응답이 성공 상태가 아닙니다."
                                );
                            }
                    )
                    .body(MockApiOrderResponse.class);
        } catch (MockApiDataCollectionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MockApiDataCollectionException("외부 주문 생성 요청에 실패했습니다.");
        }

        if (!matches(createResponse, request)) {
            throw new MockApiDataCollectionException("외부 주문 생성 데이터가 요청과 일치하지 않습니다.");
        }
    }

    private boolean matches(MockApiOrderResponse response, MockApiOrderRequest request) {
        return response != null && response.matches(request);
    }
}
