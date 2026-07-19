package dev.nbcsparta.assignment.nbccoffeeordersystem.infrastructure.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CoffeeOrder;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.entity.CollectionStatus;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.repository.CoffeeOrderRepository;
import dev.nbcsparta.assignment.nbccoffeeordersystem.domain.order.service.OrderCollectionDeliveryService;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * MockAPI.io 주문 수집 어댑터의 실제 HTTP 계약을 로컬 서버로 검증한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class MockApiDataCollectionClientTest {

    private static final long ORDER_ID = 1_001L;
    private static final long USER_ID = 1L;
    private static final long MENU_ID = 2L;
    private static final long PAYMENT_AMOUNT = 5_000L;
    private static final String MATCHING_RESPONSE = """
            {
              "id": "42",
              "orderId": 1001,
              "userId": 1,
              "menuId": 2,
              "paymentAmount": 5000
            }
            """;
    private static final String EXPECTED_REQUEST = """
            {
              "orderId": 1001,
              "userId": 1,
              "menuId": 2,
              "paymentAmount": 5000
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubMockApiServer stubServer;
    private MockApiDataCollectionClient client;

    /**
     * 각 테스트마다 격리된 로컬 HTTP 서버와 실제 RestClient 기반 어댑터를 구성한다.
     *
     * @throws IOException 로컬 서버를 열 수 없는 경우
     */
    @BeforeEach
    void setUp() throws IOException {
        stubServer = new StubMockApiServer();
        client = new MockApiDataCollectionClient(
                RestClient.builder().baseUrl(stubServer.baseUrl()).build()
        );
    }

    /**
     * 테스트가 끝나면 로컬 HTTP 서버를 종료한다.
     */
    @AfterEach
    void tearDown() {
        stubServer.close();
    }

    /**
     * 동일한 기존 주문 한 건을 조회하면 올바른 GET 계약만 사용하고 생성을 생략하는지 검증한다.
     */
    @Test
    void matchingExistingOrderUsesLookupContractWithoutCreatingAnotherOrder() {
        stubServer.respondToGet(200, "[" + MATCHING_RESPONSE + "]");

        assertThatCode(this::collect).doesNotThrowAnyException();

        assertThat(stubServer.methods()).containsExactly("GET");
        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
        assertThat(stubServer.getPath()).isEqualTo("/orders");
        assertThat(stubServer.getRawQuery()).isEqualTo("orderId=1001");
        assertJsonAccept(stubServer.getAccept());
    }

    /**
     * 빈 조회 결과는 정확히 한 번 JSON 주문을 생성하고 일치하는 응답을 성공으로 처리하는지 검증한다.
     *
     * @throws Exception 요청 JSON을 읽을 수 없는 경우
     */
    @Test
    void emptyLookupCreatesExactlyOneMatchingJsonOrder() throws Exception {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(201, MATCHING_RESPONSE);

        assertThatCode(this::collect).doesNotThrowAnyException();

        assertThat(stubServer.methods()).containsExactly("GET", "POST");
        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
        assertThat(stubServer.getPath()).isEqualTo("/orders");
        assertThat(stubServer.getRawQuery()).isEqualTo("orderId=1001");
        assertThat(stubServer.postPath()).isEqualTo("/orders");
        assertJsonAccept(stubServer.getAccept());
        assertThat(stubServer.postContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertJsonAccept(stubServer.postAccept());
        assertThat(objectMapper.readTree(stubServer.postBody()))
                .isEqualTo(objectMapper.readTree(EXPECTED_REQUEST));
    }

    /**
     * 동일 주문이 여러 건 조회되면 외부 데이터 충돌로 실패하고 생성을 시도하지 않는지 검증한다.
     */
    @Test
    void multipleLookupRecordsFailWithoutCreatingOrder() {
        stubServer.respondToGet(200, "[" + MATCHING_RESPONSE + "," + MATCHING_RESPONSE + "]");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 조회된 주문의 필드가 요청과 다르면 외부 데이터 충돌로 실패하는지 검증한다.
     */
    @Test
    void differentLookupPayloadFailsWithoutCreatingOrder() {
        stubServer.respondToGet(200, """
                [{
                  "id": "42",
                  "orderId": 1001,
                  "userId": 99,
                  "menuId": 2,
                  "paymentAmount": 5000
                }]
                """);

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 조회 응답이 JSON이 아니면 실패하고 생성을 시도하지 않는지 검증한다.
     */
    @Test
    void malformedLookupBodyFailsWithoutCreatingOrder() {
        stubServer.respondToGet(200, "{잘못된-json");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 조회 응답이 성공 상태가 아니면 실패하고 생성을 시도하지 않는지 검증한다.
     */
    @Test
    void nonSuccessLookupFailsWithoutCreatingOrder() {
        stubServer.respondToGet(503, "{\"message\":\"일시적인 조회 실패\"}");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 조회 응답이 리다이렉션 상태면 성공으로 처리하지 않고 생성을 시도하지 않는지 검증한다.
     */
    @Test
    void redirectLookupFailsWithoutCreatingOrder() {
        stubServer.respondToGet(302, "{\"message\":\"조회 위치 변경\"}");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 조회 응답의 외부 식별자가 없거나 공백이면 유효하지 않은 응답으로 실패하는지 검증한다.
     *
     * @param responseBody 식별자가 유효하지 않은 조회 응답
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "[{\"orderId\":1001,\"userId\":1,\"menuId\":2,\"paymentAmount\":5000}]",
            "[{\"id\":\"   \",\"orderId\":1001,\"userId\":1,\"menuId\":2,\"paymentAmount\":5000}]"
    })
    void missingOrBlankLookupIdFailsWithoutCreatingOrder(String responseBody) {
        stubServer.respondToGet(200, responseBody);

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 생성 응답이 JSON이 아니면 한 번의 POST 뒤 실패하는지 검증한다.
     */
    @Test
    void malformedCreateBodyFailsAfterOnePost() {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(201, "{잘못된-json");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
    }

    /**
     * 생성 응답이 성공 상태가 아니면 POST를 자동 재시도하지 않고 실패하는지 검증한다.
     */
    @Test
    void nonSuccessCreateFailsWithoutAutomaticPostRetry() {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(500, "{\"message\":\"일시적인 생성 실패\"}");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
        assertThat(stubServer.methods()).containsExactly("GET", "POST");
    }

    /**
     * 생성 응답이 리다이렉션 상태면 성공으로 처리하지 않고 POST를 자동 재시도하지 않는지 검증한다.
     */
    @Test
    void redirectCreateFailsWithoutAutomaticPostRetry() {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(307, "{\"message\":\"생성 위치 변경\"}");

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
        assertThat(stubServer.methods()).containsExactly("GET", "POST");
    }

    /**
     * 생성 응답의 주문 필드가 요청과 다르면 실패하는지 검증한다.
     */
    @Test
    void mismatchingCreateResponseFailsAfterOnePost() {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(201, """
                {
                  "id": "42",
                  "orderId": 1001,
                  "userId": 1,
                  "menuId": 99,
                  "paymentAmount": 5000
                }
                """);

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
    }

    /**
     * 생성 응답의 외부 식별자가 없거나 공백이면 유효하지 않은 응답으로 실패하는지 검증한다.
     *
     * @param responseBody 식별자가 유효하지 않은 생성 응답
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"orderId\":1001,\"userId\":1,\"menuId\":2,\"paymentAmount\":5000}",
            "{\"id\":\"   \",\"orderId\":1001,\"userId\":1,\"menuId\":2,\"paymentAmount\":5000}"
    })
    void missingOrBlankCreateIdFailsAfterOnePost(String responseBody) {
        stubServer.respondToGet(200, "[]");
        stubServer.respondToPost(201, responseBody);

        assertDeliveryFails();

        assertThat(stubServer.getCount()).isOne();
        assertThat(stubServer.postCount()).isOne();
    }

    /**
     * 연결 오류를 수집 예외로 변환하면서 민감한 외부 URL과 원본 예외를 제거하는지 검증한다.
     */
    @Test
    void transportFailureDoesNotExposeSecretBaseUrlOrResourceAccessCause() {
        String secretToken = "secret-mockapi-token-7f91c2";
        String secretBaseUrl = stubServer.baseUrl() + "/api/v1/" + secretToken;
        client = new MockApiDataCollectionClient(
                RestClient.builder().baseUrl(secretBaseUrl).build()
        );
        stubServer.close();

        Throwable failure = catchThrowable(this::collect);

        assertThat(failure).isInstanceOf(MockApiDataCollectionException.class);
        assertThat(failure.getMessage()).doesNotContain(secretBaseUrl, secretToken);
        assertThat(failure.getCause()).isNull();
        assertThat(stackTraceOf(failure))
                .doesNotContain(secretBaseUrl, secretToken, ResourceAccessException.class.getName());

        assertThat(stubServer.getCount()).isZero();
        assertThat(stubServer.postCount()).isZero();
    }

    /**
     * 실제 전송 서비스의 실패 로그에는 한국어 진단만 남고 민감한 외부 URL과 원본 예외가 없는지 검증한다.
     *
     * @param output 전송 서비스가 기록한 콘솔 출력
     */
    @Test
    void deliveryServiceLogDoesNotExposeSecretBaseUrlOrResourceAccessCause(CapturedOutput output) {
        String secretToken = "secret-mockapi-token-log-2d68a4";
        String secretBaseUrl = stubServer.baseUrl() + "/api/v1/" + secretToken;
        MockApiDataCollectionClient realClient = new MockApiDataCollectionClient(
                RestClient.builder().baseUrl(secretBaseUrl).build()
        );
        CoffeeOrderRepository repository = mock(CoffeeOrderRepository.class);
        CoffeeOrder order = mock(CoffeeOrder.class);
        when(repository.findByIdAndCollectionStatusForUpdate(ORDER_ID, CollectionStatus.PENDING))
                .thenReturn(Optional.of(order));
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getUserId()).thenReturn(USER_ID);
        when(order.getMenuId()).thenReturn(MENU_ID);
        when(order.getPaymentAmount()).thenReturn(PAYMENT_AMOUNT);
        OrderCollectionDeliveryService deliveryService = new OrderCollectionDeliveryService(repository, realClient);
        stubServer.close();

        deliveryService.deliver(ORDER_ID);

        String capturedLog = output.getOut() + output.getErr();
        assertThat(capturedLog)
                .contains("주문 데이터 수집 전송에 실패했습니다. 주문=1001")
                .doesNotContain(secretBaseUrl, secretToken, ResourceAccessException.class.getName());
        verify(order, never()).markCollectionSucceeded();
    }

    private void collect() {
        client.collect(ORDER_ID, USER_ID, MENU_ID, PAYMENT_AMOUNT);
    }

    private void assertDeliveryFails() {
        assertThatThrownBy(this::collect)
                .isInstanceOf(MockApiDataCollectionException.class);
    }

    private void assertJsonAccept(String accept) {
        assertThat(accept).isNotNull();
        assertThat(MediaType.parseMediaTypes(accept))
                .anySatisfy(mediaType -> assertThat(mediaType.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
    }

    private String stackTraceOf(Throwable failure) {
        StringWriter stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        return stackTrace.toString();
    }

    /**
     * 어댑터 요청과 미리 지정한 응답을 기록하는 로컬 MockAPI 대역 서버다.
     */
    private static final class StubMockApiServer implements AutoCloseable {

        private final HttpServer server;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger postCount = new AtomicInteger();
        private final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<String> getPath = new AtomicReference<>();
        private final AtomicReference<String> getRawQuery = new AtomicReference<>();
        private final AtomicReference<String> getAccept = new AtomicReference<>();
        private final AtomicReference<String> postPath = new AtomicReference<>();
        private final AtomicReference<String> postAccept = new AtomicReference<>();
        private final AtomicReference<String> postContentType = new AtomicReference<>();
        private final AtomicReference<String> postBody = new AtomicReference<>();

        private volatile int getStatus = 200;
        private volatile String getResponseBody = "[]";
        private volatile int postStatus = 201;
        private volatile String postResponseBody = MATCHING_RESPONSE;

        private StubMockApiServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/orders", this::handle);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void respondToGet(int status, String responseBody) {
            getStatus = status;
            getResponseBody = responseBody;
        }

        private void respondToPost(int status, String responseBody) {
            postStatus = status;
            postResponseBody = responseBody;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                methods.add(exchange.getRequestMethod());
                if ("GET".equals(exchange.getRequestMethod())) {
                    handleGet(exchange);
                    return;
                }
                if ("POST".equals(exchange.getRequestMethod())) {
                    handlePost(exchange);
                    return;
                }
                sendJson(exchange, 405, "{\"message\":\"허용하지 않는 요청 방식\"}");
            } finally {
                exchange.close();
            }
        }

        private void handleGet(HttpExchange exchange) throws IOException {
            getCount.incrementAndGet();
            getPath.set(exchange.getRequestURI().getRawPath());
            getRawQuery.set(exchange.getRequestURI().getRawQuery());
            getAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            sendJson(exchange, getStatus, getResponseBody);
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            postCount.incrementAndGet();
            postPath.set(exchange.getRequestURI().getRawPath());
            postAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            postContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            postBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, postStatus, postResponseBody);
        }

        private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
        }

        private int getCount() {
            return getCount.get();
        }

        private int postCount() {
            return postCount.get();
        }

        private List<String> methods() {
            synchronized (methods) {
                return List.copyOf(methods);
            }
        }

        private String getRawQuery() {
            return getRawQuery.get();
        }

        private String getPath() {
            return getPath.get();
        }

        private String getAccept() {
            return getAccept.get();
        }

        private String postAccept() {
            return postAccept.get();
        }

        private String postPath() {
            return postPath.get();
        }

        private String postContentType() {
            return postContentType.get();
        }

        private String postBody() {
            return postBody.get();
        }

        /**
         * 이미 종료된 경우를 포함해 안전하게 로컬 서버를 닫는다.
         */
        @Override
        public void close() {
            if (running.compareAndSet(true, false)) {
                server.stop(0);
            }
        }
    }
}
