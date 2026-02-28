package com.langdong.spare.service;

import com.langdong.spare.dto.WarningItemDTO;
import com.langdong.spare.mapper.WarningMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WarningService {

    @Autowired
    private WarningMapper warningMapper;

    public List<WarningItemDTO> getLowStockWarnings() {
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
        List<WarningItemDTO> lowStock = getLowStockWarnings();
        List<WarningItemDTO> overdueWO = getOverdueWorkOrders();
        List<WarningItemDTO> overduePO = getOverduePurchaseOrders();
        return Map.of(
                "lowStock", lowStock,
                "overdueWO", overdueWO,
                "overduePO", overduePO,
                "totalCount", lowStock.size() + overdueWO.size() + overduePO.size());
    }
}
