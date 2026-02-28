package com.langdong.spare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步任务与定时任务配置
 *
 * @EnableAsync     启用 @Async 注解支持（用于分类重算的异步执行）
 * @EnableScheduling 启用 @Scheduled 注解支持（用于每月1日凌晨定时触发）
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncScheduleConfig {
    // 使用 Spring 默认线程池，无需额外配置
}
