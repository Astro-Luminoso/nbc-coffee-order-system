package dev.nbcsparta.assignment.nbccoffeeordersystem.global.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * MockAPI 설정 바인딩과 전용 HTTP 클라이언트 동작을 검증한다.
 */
class MockApiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockApiConfig.class);

    /**
     * 기본 URL이 없어도 컨텍스트와 HTTP 클라이언트 빈이 생성되는지 검증한다.
     */
    @Test
    void contextStartsWithoutBaseUrl() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MockApiProperties.class);
            assertThat(context).hasBean("mockApiRestClient");
            assertThat(context.getBean("mockApiRestClient")).isInstanceOf(RestClient.class);
        });
    }

    /**
     * 별도 설정이 없을 때 기본 제한 시간이 적용되는지 검증한다.
     */
    @Test
    void propertiesUseDefaultTimeouts() {
        contextRunner.run(context -> {
            MockApiProperties properties = context.getBean(MockApiProperties.class);

            assertThat(properties.baseUrl()).isNull();
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    /**
     * 외부 설정으로 기본 URL과 제한 시간을 덮어쓸 수 있는지 검증한다.
     */
    @Test
    void propertiesBindConfiguredValues() {
        contextRunner
                .withPropertyValues(
                        "mockapi.base-url=http://127.0.0.1:8089/api/v1",
                        "mockapi.connect-timeout=PT1S",
                        "mockapi.read-timeout=PT3S"
                )
                .run(context -> {
                    MockApiProperties properties = context.getBean(MockApiProperties.class);

                    assertThat(properties.baseUrl()).isEqualTo("http://127.0.0.1:8089/api/v1");
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    /**
     * 연결 및 읽기 제한 시간이 요청 팩터리에 전달되는지 검증한다.
     */
    @Test
    void requestFactoryUsesConfiguredTimeouts() {
        MockApiProperties properties = new MockApiProperties(
                "http://127.0.0.1:8089/api/v1",
                Duration.ofMillis(125),
                Duration.ofMillis(375)
        );

        JdkClientHttpRequestFactory requestFactory = new MockApiConfig().createRequestFactory(properties);
        HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");

        assertThat(httpClient).isNotNull();
        assertThat(httpClient.connectTimeout()).contains(Duration.ofMillis(125));
        assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
                .isEqualTo(Duration.ofMillis(375));
    }

    /**
     * 기본 URL이 없을 때 실제 요청이 명확한 오류로 실패하는지 검증한다.
     */
    @Test
    void requestFailsClearlyWithoutBaseUrl() {
        contextRunner.run(context -> {
            RestClient restClient = context.getBean("mockApiRestClient", RestClient.class);

            assertThatThrownBy(() -> restClient.get()
                    .uri("/orders")
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("MockAPI 기본 URL이 설정되지 않았습니다.");
        });
    }

    /**
     * 기본 URL과 JSON 요청 헤더가 실제 요청에 적용되는지 검증한다.
     *
     * @throws IOException 로컬 검증 서버를 시작하지 못한 경우
     */
    @Test
    void restClientUsesBaseUrlAndJsonHeaders() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> acceptHeader = new AtomicReference<>();
        AtomicReference<String> contentTypeHeader = new AtomicReference<>();
        HttpServer server = createServer(exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            acceptHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.ACCEPT));
            contentTypeHeader.set(exchange.getRequestHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            exchange.getRequestBody().readAllBytes();
            byte[] responseBody = "{}".getBytes(UTF_8);
            exchange.getResponseHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });

        try {
            RestClient restClient = createRestClient(server);

            restClient.post()
                    .uri("/orders")
                    .body(Map.of("orderId", 1L))
                    .retrieve()
                    .toBodilessEntity();

            assertThat(requestPath).hasValue("/api/v1/orders");
            assertThat(acceptHeader).hasValue(MediaType.APPLICATION_JSON_VALUE);
            assertThat(contentTypeHeader).hasValue(MediaType.APPLICATION_JSON_VALUE);
        } finally {
            server.stop(0);
        }
    }

    /**
     * 실패한 POST 요청을 HTTP 클라이언트가 자동 재시도하지 않는지 검증한다.
     *
     * @throws IOException 로컬 검증 서버를 시작하지 못한 경우
     */
    @Test
    void restClientDoesNotRetryPostAutomatically() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = createServer(exchange -> {
            requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });

        try {
            RestClient restClient = createRestClient(server);

            assertThatThrownBy(() -> restClient.post()
                    .uri("/orders")
                    .body(Map.of("orderId", 1L))
                    .retrieve()
                    .toBodilessEntity())
                    .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);
            assertThat(requestCount).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    private RestClient createRestClient(HttpServer server) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1///";
        MockApiProperties properties = new MockApiProperties(
                baseUrl,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
        return new MockApiConfig().mockApiRestClient(properties);
    }

    private HttpServer createServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }
}
