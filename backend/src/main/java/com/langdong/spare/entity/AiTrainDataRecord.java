package com.langdong.spare.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AiTrainDataRecord {
    private Long id;
    private LocalDate bizDate;
    private Long sparePartId;
    private String partCode;
    private Integer dailyOutboundQty;
    private Integer dailyRequisitionApplyQty;
    private Integer dailyRequisitionOutQty;
    private Integer dailyInstallQty;
    private Integer dailyWorkOrderCnt;
    private Integer dailyPurchaseArrivalQty;
    private Integer dailyPurchaseArrivalOrders;
    private Integer dayOfWeek;
    private Integer isWeekend;
    private String sourceLevel;
    private Integer isImputed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
