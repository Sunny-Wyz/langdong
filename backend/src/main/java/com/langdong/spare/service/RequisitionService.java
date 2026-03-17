package com.langdong.spare.service;

import com.langdong.spare.dto.*;
import com.langdong.spare.entity.Requisition;
import com.langdong.spare.entity.RequisitionItem;
import com.langdong.spare.entity.StockInItem;
import com.langdong.spare.mapper.RequisitionItemMapper;
import com.langdong.spare.mapper.RequisitionMapper;
import com.langdong.spare.mapper.SparePartStockMapper;
import com.langdong.spare.mapper.StockInItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RequisitionService {

    @Autowired
    private RequisitionMapper requisitionMapper;

    @Autowired
    private RequisitionItemMapper requisitionItemMapper;

    @Autowired
    private SparePartStockMapper sparePartStockMapper;

    @Autowired
    private StockInItemMapper stockInItemMapper;

    @Transactional
    public void apply(RequisitionApplyDTO dto, Long userId) {
        Requisition req = new Requisition();
        req.setReqNo("REQ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        req.setApplicantId(userId);
        req.setWorkOrderNo(dto.getWorkOrderNo());
        req.setDeviceId(dto.getDeviceId());
        req.setReqStatus("PENDING");
        req.setIsUrgent(dto.getIsUrgent() != null ? dto.getIsUrgent() : false);
        req.setRemark(dto.getRemark());

        requisitionMapper.insert(req);

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            List<RequisitionItem> items = dto.getItems().stream().map(i -> {
                RequisitionItem item = new RequisitionItem();
                item.setReqId(req.getId());
                item.setSparePartId(i.getSparePartId());
                item.setApplyQty(i.getApplyQty());
                return item;
            }).collect(Collectors.toList());
            requisitionItemMapper.insertBatch(items);
        }
    }

    public List<Requisition> getList(String status, Long applicantId) {
        return requisitionMapper.findList(status, applicantId);
    }

    public Requisition getDetail(Long id) {
        return requisitionMapper.findWithDetailsById(id);
    }

    public List<RequisitionItem> getItems(Long reqId) {
        return requisitionItemMapper.findByReqId(reqId);
    }

    @Transactional
    public void approve(Long id, RequisitionApproveDTO dto, Long approverId) {
        String status = "APPROVE".equalsIgnoreCase(dto.getAction()) ? "APPROVED" : "REJECTED";
        requisitionMapper.updateApprovalInfo(id, approverId, dto.getRemark(), status);
    }

    @Transactional
    public void outbound(Long id, RequisitionOutboundDTO dto) {
        List<RequisitionItem> allItems = requisitionItemMapper.findByReqId(id);

        for (RequisitionOutboundDTO.RequisitionOutboundItemDTO itemDto : dto.getItems()) {
            if (itemDto.getOutQty() == null || itemDto.getOutQty() <= 0) continue;

            RequisitionItem item = allItems.stream()
                    .filter(i -> i.getId().equals(itemDto.getItemId()))
                    .findFirst().orElse(null);
            if (item == null) continue;

            // FIFO：按入库日期升序消耗批次
            int needed = itemDto.getOutQty();
            List<StockInItem> fifoBatches = stockInItemMapper.findFifoBySparePartId(item.getSparePartId());
            for (StockInItem batch : fifoBatches) {
                if (needed <= 0) break;
                int consume = Math.min(batch.getRemainingQty(), needed);
                stockInItemMapper.deductRemainingQty(batch.getId(), consume);
                needed -= consume;
            }
            if (needed > 0) {
                throw new RuntimeException("备件库存不足，无法完成出库（缺少 " + needed + " 件）");
            }

            requisitionItemMapper.updateOutbound(itemDto.getItemId(), itemDto.getOutQty());
            sparePartStockMapper.addQuantity(item.getSparePartId(), -itemDto.getOutQty());
        }
        requisitionMapper.updateStatus(id, "OUTBOUND");
    }

    @Transactional
    public void install(Long id, RequisitionInstallDTO dto, Long installerId) {
        for (RequisitionInstallDTO.RequisitionInstallItemDTO itemDto : dto.getItems()) {
            requisitionItemMapper.updateInstallInfo(itemDto.getItemId(), installerId, itemDto.getInstallLoc());
        }
        requisitionMapper.updateStatus(id, "INSTALLED");
    }
}
