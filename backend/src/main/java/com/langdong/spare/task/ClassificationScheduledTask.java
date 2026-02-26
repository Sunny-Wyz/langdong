package com.langdong.spare.task;

import com.langdong.spare.service.SmartClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ClassificationScheduledTask {

    @Autowired
    private SmartClassificationService classificationService;

    // 每月1号凌晨0点执行一次分类重算
    @Scheduled(cron = "0 0 0 1 * ?")
    public void executeMonthlyClassification() {
        System.out.println("开始执行每月的备件智能分类重算任务...");
        classificationService.calculateAllClassifications();
        System.out.println("每月的备件智能分类重算任务执行完毕。");
    }
}
