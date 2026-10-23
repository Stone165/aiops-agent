package com.aiops.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * DeepSeek API 配置。
 *
 * <p>API Key 只允许通过环境变量 DEEPSEEK_API_KEY 注入，禁止写入源码或配置文件。</p>
 */
@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {

    private String baseUrl = "https://api.deepseek.com";

    private String apiKey = "";

    private String model = "deepseek-flash";

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(60);

    private int maxToolRounds = 5;
}
