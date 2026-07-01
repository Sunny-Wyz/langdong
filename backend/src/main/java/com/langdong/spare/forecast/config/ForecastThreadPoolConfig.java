package com.langdong.spare.forecast.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 需求预测专用线程池配置。
 *
 * <p>训练/回测等长耗时任务走独立线程池 {@code forecastExecutor}，与在线请求线程池
 * 物理隔离，避免月度全量重算拖垮前端接口响应（提示词硬约束）。</p>
 *
 * <p>同时通过 {@link EnableConfigurationProperties} 激活预测相关配置属性绑定。</p>
 */
@Configuration
@EnableConfigurationProperties({XGBoostProperties.class, ForecastProperties.class})
public class ForecastThreadPoolConfig {

    /** 预测异步任务线程池 Bean 名称，供 {@code @Async("forecastExecutor")} 引用。 */
    public static final String FORECAST_EXECUTOR = "forecastExecutor";

    @Bean(FORECAST_EXECUTOR)
    public ThreadPoolTaskExecutor forecastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // XGBoost 训练本身多线程，核心线程数保持较小，避免线程争抢
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("forecast-");
        executor.setKeepAliveSeconds(120);
        // 队列满时由调用线程执行，保证任务不丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
