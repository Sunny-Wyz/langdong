package com.langdong.spare.forecast.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * 需求预测专用线程配置。
 *
 * <p>已升级为 Java 21 虚拟线程支持。训练/回测等长耗时任务走独立虚拟线程，
 * 避免物理线程池排队和高并发下的争抢，同时通过 SimpleAsyncTaskExecutor 保持与 {@code @Async("forecastExecutor")} 的兼容性。</p>
 *
 * <p>同时通过 {@link EnableConfigurationProperties} 激活预测相关配置属性绑定。</p>
 */
@Configuration
@EnableConfigurationProperties({XGBoostProperties.class, ForecastProperties.class})
public class ForecastThreadPoolConfig {

    /** 预测异步任务线程池 Bean 名称，供 {@code @Async("forecastExecutor")} 引用。 */
    public static final String FORECAST_EXECUTOR = "forecastExecutor";

    @Bean(FORECAST_EXECUTOR)
    public AsyncTaskExecutor forecastExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setThreadNamePrefix("forecast-");
        return executor;
    }
}
