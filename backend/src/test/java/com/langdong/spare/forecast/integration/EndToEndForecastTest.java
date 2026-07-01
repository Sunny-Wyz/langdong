package com.langdong.spare.forecast.integration;

import com.langdong.spare.controller.ForecastController;
import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.forecast.scheduler.MonthlyForecastScheduler;
import com.langdong.spare.mapper.AiForecastResultMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST 接口端到端集成测试（EndToEndForecastTest）。
 *
 * <p>基于 Standalone MockMvc 模式运行，不依赖 live DB 与 Spring 容器启动，
 * 高性能校验 Controller 的 URL 路由、输入校验、服务分发与响应 JSON（六标准字段）的序列化。</p>
 */
public class EndToEndForecastTest {

    private MockMvc mockMvc;
    private AiForecastResultMapper mapper;
    private MonthlyForecastScheduler scheduler;

    @BeforeEach
    void setup() {
        mapper = mock(AiForecastResultMapper.class);
        scheduler = mock(MonthlyForecastScheduler.class);
        ForecastController controller = new ForecastController(mapper, scheduler);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("接口测试：分页联查成功返回含六标准字段的 JSON 数据")
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

        when(mapper.findByPage("2026-07", "SP001", 0, 10))
                .thenReturn(Collections.singletonList(mockResult));
        when(mapper.countByPage("2026-07", "SP001")).thenReturn(1L);

        mockMvc.perform(get("/api/v1/forecast/result")
                        .param("month", "2026-07")
                        .param("partCode", "SP001")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list[0].partCode").value("SP001"))
                .andExpect(jsonPath("$.list[0].partName").value("气缸电磁阀"))
                .andExpect(jsonPath("$.list[0].occurrenceProb").value(0.8500))
                .andExpect(jsonPath("$.list[0].positiveQty").value(10.0))
                .andExpect(jsonPath("$.list[0].predictQty").value(8.5))
                .andExpect(jsonPath("$.list[0].lowerBound").value(4.0))
                .andExpect(jsonPath("$.list[0].upperBound").value(13.0))
                .andExpect(jsonPath("$.list[0].leadTimeQuantile").value(11.5))
                .andExpect(jsonPath("$.list[0].safetyStock").value(5))
                .andExpect(jsonPath("$.list[0].reorderPoint").value(11));
    }

    @Test
    @DisplayName("接口测试：参数格式非法返回 Bad Request 400")
    void testQueryApiBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/forecast/result")
                        .param("month", "2026/07"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("接口测试：手动重算触发成功，异步推进计算流水线")
    void testTriggerApi() throws Exception {
        mockMvc.perform(post("/api/v1/forecast/trigger")
                        .param("month", "2026-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.message").value("两阶段AI预测重算与安全库存分析任务已启动"));

        verify(scheduler, times(1)).triggerForecastPipeline("2026-07");
    }
}
