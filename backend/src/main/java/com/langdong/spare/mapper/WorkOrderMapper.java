package com.langdong.spare.mapper;

import com.langdong.spare.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkOrderMapper {

    int insert(WorkOrder workOrder);

    WorkOrder findById(Long id);

    List<WorkOrder> findList(@Param("orderStatus") String orderStatus,
                             @Param("deviceId") Long deviceId,
                             @Param("faultLevel") String faultLevel,
                             @Param("startTime") String startTime,
                             @Param("endTime") String endTime);

    int updateAssign(@Param("id") Long id,
                     @Param("assigneeId") Long assigneeId,
                     @Param("planFinish") LocalDateTime planFinish);

    int updateProcess(@Param("id") Long id,
                      @Param("faultCause") String faultCause,
                      @Param("repairMethod") String repairMethod);

    int updateComplete(@Param("id") Long id,
                       @Param("actualFinish") LocalDateTime actualFinish,
                       @Param("laborCost") BigDecimal laborCost,
                       @Param("outsourceCost") BigDecimal outsourceCost,
                       @Param("mttrMinutes") int mttrMinutes,
                       @Param("partCost") BigDecimal partCost);

    BigDecimal sumPartCostByWorkOrderNo(@Param("workOrderNo") String workOrderNo);
}
