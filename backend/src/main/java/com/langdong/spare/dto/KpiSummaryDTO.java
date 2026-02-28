package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 管理层驾驶舱 5 项核心 KPI
 */
@Data
public class KpiSummaryDTO {
    private BigDecimal totalInventoryAmount; // 库存总金额
    private BigDecimal inventoryTurnoverRate; // 库存周转率（次/年）
    private BigDecimal monthPurchaseAmount; // 本月采购额
    private BigDecimal monthRepairCost; // 本月维修费用
    private BigDecimal equipmentAvailability; // 设备可用率（%）
}
