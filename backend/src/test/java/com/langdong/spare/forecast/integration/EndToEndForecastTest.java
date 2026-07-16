package com.langdong.spare.forecast.integration;

import com.langdong.spare.controller.AiForecastController;
import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.forecast.scheduler.MonthlyForecastScheduler;
import com.langdong.spare.service.ai.AiForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 需求预测主入口端到端测试（仅两阶段 Hurdle-Gamma 入口 /api/ai/forecast）。
 */
public class EndToEndForecastTest {

    private MockMvc mockMvc;
    private AiForecastService aiForecastService;
    private MonthlyForecastScheduler scheduler;

    @BeforeEach
    void setup() {
        aiForecastService = mock(AiForecastService.class);
        scheduler = mock(MonthlyForecastScheduler.class);
        AiForecastController controller = new AiForecastController();
        ReflectionTestUtils.setField(controller, "aiForecastService", aiForecastService);
        ReflectionTestUtils.setField(controller, "monthlyForecastScheduler", scheduler);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("分页查询预测结果返回列表与总数")
    void testQueryApi() throws Exception {
        AiForecastResult mockResult = new AiForecastResult();
        mockResult.setPartCode("SP001");
        mockResult.setPartName("气缸电磁阀");
        mockResult.setForecastMonth("2026-07");
        mockResult.setPredictQty(BigDecimal.valueOf(8.5));
        mockResult.setLowerBound(BigDecimal.valueOf(4.0));
        mockResult.setUpperBound(BigDecimal.valueOf(13.0));
        mockResult.setOccurrenceProb(BigDecimal.valueOf(0.8500));
        mockResult.setPositiveQty(BigDecimal.valueOf(10.0));
        mockResult.setLeadTimeQuantile(BigDecimal.valueOf(11.5));
        mockResult.setSafetyStock(5);
        mockResult.setReorderPoint(11);
        mockResult.setServiceLevel(BigDecimal.valueOf(99.0));

        Map<String, Object> serviceResult = new HashMap<>();
        serviceResult.put("total", 1L);
        serviceResult.put("list", Collections.singletonList(mockResult));
        when(aiForecastService.queryResult("2026-07", "SP001", 1, 10)).thenReturn(serviceResult);

        mockMvc.perform(get("/api/ai/forecast/result")
                        .param("month", "2026-07")
                        .param("partCode", "SP001")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list[0].partCode").value("SP001"))
                .andExpect(jsonPath("$.list[0].occurrenceProb").value(0.8500))
                .andExpect(jsonPath("$.list[0].predictQty").value(8.5));
    }

    @Test
    @DisplayName("非法月份格式返回 400")
    void testQueryApiBadRequest() throws Exception {
        mockMvc.perform(get("/api/ai/forecast/result")
                        .param("month", "2026/07"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("手动重算触发两阶段 Hurdle-Gamma 流水线")
    void testTriggerApi() throws Exception {
        Map<String, Object> runStatus = new HashMap<>();
        runStatus.put("status", "RUNNING");
        runStatus.put("percent", 1);
        when(scheduler.getRunStatus()).thenReturn(runStatus);

        mockMvc.perform(post("/api/ai/forecast/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("两阶段 Hurdle-Gamma 预测重算与安全库存分析任务已启动"));

        verify(scheduler, times(1)).triggerForecastPipeline(anyString());
    }
}
