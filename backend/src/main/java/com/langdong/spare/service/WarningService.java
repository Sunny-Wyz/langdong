package com.langdong.spare.service;

import com.langdong.spare.dto.WarningItemDTO;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import com.langdong.spare.mapper.WarningMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class WarningService {

    @Autowired
    private WarningMapper warningMapper;

    @Autowired
    private ReorderSuggestMapper reorderSuggestMapper;

    public List<WarningItemDTO> getLowStockWarnings() {
        ensurePendingSuggestionReady();
        return warningMapper.getLowStockWarnings();
    }

    public List<WarningItemDTO> getOverdueWorkOrders() {
        return warningMapper.getOverdueWorkOrders();
    }

    public List<WarningItemDTO> getOverduePurchaseOrders() {
        return warningMapper.getOverduePurchaseOrders();
    }

    /** 所有预警汇总，附带各类型 count */
    public Map<String, Object> getAllWarnings() {
        ensurePendingSuggestionReady();
        List<WarningItemDTO> lowStock = getLowStockWarnings();
        List<WarningItemDTO> overdueWO = getOverdueWorkOrders();
        List<WarningItemDTO> overduePO = getOverduePurchaseOrders();
        return Map.of(
                "lowStock", lowStock,
                "overdueWO", overdueWO,
                "overduePO", overduePO,
                "totalCount", lowStock.size() + overdueWO.size() + overduePO.size());
    }

    private void ensurePendingSuggestionReady() {
        if (reorderSuggestMapper.countByStatus("待处理") > 0) {
            return;
        }
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        reorderSuggestMapper.bootstrapPendingSuggestions(month);
    }
}
