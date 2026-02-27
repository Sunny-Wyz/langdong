package com.langdong.spare.service;

import com.langdong.spare.dto.WorkOrderAssignDTO;
import com.langdong.spare.dto.WorkOrderCompleteDTO;
import com.langdong.spare.dto.WorkOrderProcessDTO;
import com.langdong.spare.dto.WorkOrderReportDTO;
import com.langdong.spare.entity.WorkOrder;
import com.langdong.spare.mapper.WorkOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class WorkOrderService {

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Transactional
    public void report(WorkOrderReportDTO dto, Long userId) {
        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNo("WO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        wo.setDeviceId(dto.getDeviceId());
        wo.setReporterId(userId);
        wo.setFaultDesc(dto.getFaultDesc());
        wo.setFaultLevel(dto.getFaultLevel());
        wo.setOrderStatus("报修");
        wo.setReportTime(LocalDateTime.now());
        workOrderMapper.insert(wo);
    }

    public List<WorkOrder> getList(String orderStatus, Long deviceId, String faultLevel,
                                   String startTime, String endTime) {
        return workOrderMapper.findList(orderStatus, deviceId, faultLevel, startTime, endTime);
    }

    public WorkOrder getDetail(Long id) {
        return workOrderMapper.findById(id);
    }

    @Transactional
    public void assign(Long id, WorkOrderAssignDTO dto) {
        WorkOrder wo = workOrderMapper.findById(id);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"报修".equals(wo.getOrderStatus())) {
            throw new RuntimeException("当前工单状态不允许派工，当前状态：" + wo.getOrderStatus());
        }
        int rows = workOrderMapper.updateAssign(id, dto.getAssigneeId(), dto.getPlanFinish());
        if (rows == 0) {
            throw new RuntimeException("派工失败，请刷新后重试");
        }
    }

    @Transactional
    public void process(Long id, WorkOrderProcessDTO dto) {
        WorkOrder wo = workOrderMapper.findById(id);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"已派工".equals(wo.getOrderStatus())) {
            throw new RuntimeException("当前工单状态不允许填写维修记录，当前状态：" + wo.getOrderStatus());
        }
        int rows = workOrderMapper.updateProcess(id, dto.getFaultCause(), dto.getRepairMethod());
        if (rows == 0) {
            throw new RuntimeException("更新失败，请刷新后重试");
        }
    }

    @Transactional
    public void complete(Long id, WorkOrderCompleteDTO dto) {
        WorkOrder wo = workOrderMapper.findById(id);
        if (wo == null) {
            throw new RuntimeException("工单不存在");
        }
        if (!"维修中".equals(wo.getOrderStatus())) {
            throw new RuntimeException("当前工单状态不允许完工确认，当前状态：" + wo.getOrderStatus());
        }

        // 计算本次维修时长（分钟）
        int mttrMinutes = (int) ChronoUnit.MINUTES.between(wo.getReportTime(), dto.getActualFinish());

        // 自动汇总关联领用单中的备件费用
        BigDecimal partCost = workOrderMapper.sumPartCostByWorkOrderNo(wo.getWorkOrderNo());
        if (partCost == null) {
            partCost = BigDecimal.ZERO;
        }

        workOrderMapper.updateComplete(id, dto.getActualFinish(),
                dto.getLaborCost() != null ? dto.getLaborCost() : BigDecimal.ZERO,
                dto.getOutsourceCost() != null ? dto.getOutsourceCost() : BigDecimal.ZERO,
                mttrMinutes, partCost);
    }
}
