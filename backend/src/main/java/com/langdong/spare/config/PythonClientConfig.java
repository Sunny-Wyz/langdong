package com.langdong.spare.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PythonClientConfig {

    @Bean
    public RestTemplate pythonRestTemplate(RestTemplateBuilder builder) {
        // 叙事多基线回测可能数分钟
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofMinutes(15))
                .build();
    }
}
