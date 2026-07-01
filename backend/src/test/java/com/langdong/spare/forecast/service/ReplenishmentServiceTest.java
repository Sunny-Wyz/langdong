package com.langdong.spare.forecast.service;

import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * ReplenishmentService 智能补货建议计算服务单元测试（对应测试 TC-PO-01）。
 */
public class ReplenishmentServiceTest {

    @Test
    @DisplayName("智能补货：低于 ROP 推送、高于 ROP 忽略、低于 SS 紧急、按紧急程度排序且保障幂等")
    void testGenerateSuggestions() {
        ReorderSuggestMapper mapper = mock(ReorderSuggestMapper.class);
        ReplenishmentService service = new ReplenishmentService(mapper);

        // 备件 P001: 库存 5 <= ROP 10，低于 SS 6 -> 紧急，建议采购 5 件
        ForecastResult f1 = new ForecastResult();
        f1.setPartCode("P001");
        f1.setDemandHat(8.0);
        f1.setReorderPoint(10);
        f1.setSafetyStock(6);
        f1.setLowerBound(4.0);
        f1.setUpperBound(12.0);

        // 备件 P002: 库存 8 <= ROP 12，但 >= SS 5 -> 正常，建议采购 4 件
        ForecastResult f2 = new ForecastResult();
        f2.setPartCode("P002");
        f2.setDemandHat(6.0);
        f2.setReorderPoint(12);
        f2.setSafetyStock(5);
        f2.setLowerBound(3.0);
        f2.setUpperBound(9.0);

        // 备件 P003: 库存 15 > ROP 10 -> 高于补货水位，应忽略
        ForecastResult f3 = new ForecastResult();
        f3.setPartCode("P003");
        f3.setDemandHat(5.0);
        f3.setReorderPoint(10);
        f3.setSafetyStock(4);

        // 备件 P004: 数据不足 -> 应忽略
        ForecastResult f4 = ForecastResult.insufficient("P004", "2026-07", "历史数据不足");

        // Mock 当前可用库存
        when(mapper.findCurrentStockByPartCode("P001")).thenReturn(5);
        when(mapper.findCurrentStockByPartCode("P002")).thenReturn(8);
        when(mapper.findCurrentStockByPartCode("P003")).thenReturn(15);

        List<ReorderSuggest> res = service.generateReplenishmentSuggestions("2026-07", Arrays.asList(f1, f2, f3, f4));

        // 验证只为低于 ROP 且数据充足的备件生成了补货建议
        assertEquals(2, res.size(), "应该生成 2 条建议");

        // 验证第一条：紧急程度高的排在最前 (P001)
        assertEquals("P001", res.get(0).getPartCode());
        assertEquals("紧急", res.get(0).getUrgency());
        assertEquals(5, res.get(0).getSuggestQty(), "建议采购量应为 ROP - CurrentStock");

        // 验证第二条：正常排在后面 (P002)
        assertEquals("P002", res.get(1).getPartCode());
        assertEquals("正常", res.get(1).getUrgency());
        assertEquals(4, res.get(1).getSuggestQty(), "建议采购量应为 ROP - CurrentStock");

        // 验证幂等删除：插入前先删除旧的待处理建议
        verify(mapper, times(1)).deletePendingByPartAndMonth("P001", "2026-07");
        verify(mapper, times(1)).deletePendingByPartAndMonth("P002", "2026-07");
        verify(mapper, never()).deletePendingByPartAndMonth("P003", "2026-07");

        // 验证批量插入
        ArgumentCaptor<ReorderSuggest> captor = ArgumentCaptor.forClass(ReorderSuggest.class);
        verify(mapper, times(2)).insert(captor.capture());
        List<ReorderSuggest> inserted = captor.getAllValues();
        assertEquals("P001", inserted.get(0).getPartCode());
        assertEquals("P002", inserted.get(1).getPartCode());
    }
}
