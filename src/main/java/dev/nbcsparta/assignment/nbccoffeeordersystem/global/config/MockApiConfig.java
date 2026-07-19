package dev.nbcsparta.assignment.nbccoffeeordersystem.global.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * MockAPI 전용 HTTP 클라이언트를 구성한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MockApiProperties.class)
public class MockApiConfig {

    private static final String MISSING_BASE_URL_MESSAGE = "MockAPI 기본 URL이 설정되지 않았습니다.";

    /**
     * MockAPI 요청에 공통으로 적용할 기본 URL, 제한 시간, JSON 헤더를 구성한다.
     *
     * @param properties MockAPI 외부 설정
     * @return MockAPI 전용 REST 클라이언트
     */
    @Bean("mockApiRestClient")
    public RestClient mockApiRestClient(MockApiProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(createRequestFactory(properties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        String baseUrl = normalizeBaseUrl(properties.baseUrl());
        if (StringUtils.hasText(baseUrl)) {
            return builder.baseUrl(baseUrl).build();
        }

        return builder.requestInterceptor((request, body, execution) -> {
            throw new IllegalStateException(MISSING_BASE_URL_MESSAGE);
        }).build();
    }

    JdkClientHttpRequestFactory createRequestFactory(MockApiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }

        return baseUrl.trim().replaceFirst("/+$", "");
    }
}
