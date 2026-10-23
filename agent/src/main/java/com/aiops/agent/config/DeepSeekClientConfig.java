package com.aiops.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 构建带超时控制的 DeepSeek HTTP 客户端。
 */
@Configuration
public class DeepSeekClientConfig {

    @Bean
    public RestClient deepSeekRestClient(RestClient.Builder builder,
                                         DeepSeekProperties properties) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(
                toMillis(properties.getConnectTimeout(), 5000));
        requestFactory.setReadTimeout(
                toMillis(properties.getReadTimeout(), 60000));

        String baseUrl = properties.getBaseUrl() == null
                ? "https://api.deepseek.com"
                : properties.getBaseUrl().replaceAll("/+$", "");

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private int toMillis(java.time.Duration duration, int fallback) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return fallback;
        }
        return Math.toIntExact(duration.toMillis());
    }
}
