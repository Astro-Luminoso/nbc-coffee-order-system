package dev.nbcsparta.assignment.nbccoffeeordersystem.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * MockAPI 연동에 필요한 외부 설정을 제공한다.
 *
 * @param baseUrl MockAPI 프로젝트 기본 URL
 * @param connectTimeout 연결 제한 시간
 * @param readTimeout 응답 읽기 제한 시간
 */
@ConfigurationProperties(prefix = "mockapi")
public record MockApiProperties(
        String baseUrl,
        @DefaultValue("PT2S") Duration connectTimeout,
        @DefaultValue("PT5S") Duration readTimeout
) {
}
