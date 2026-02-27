package com.langdong.spare.service;

import com.langdong.spare.dto.*;
import com.langdong.spare.entity.Requisition;
import com.langdong.spare.entity.RequisitionItem;
import com.langdong.spare.mapper.RequisitionItemMapper;
import com.langdong.spare.mapper.RequisitionMapper;
import com.langdong.spare.mapper.SparePartStockMapper;
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
        for (RequisitionOutboundDTO.RequisitionOutboundItemDTO itemDto : dto.getItems()) {
            requisitionItemMapper.updateOutbound(itemDto.getItemId(), itemDto.getOutQty());

            // 扣除库存
            RequisitionItem item = requisitionItemMapper.findByReqId(id).stream()
                    .filter(i -> i.getId().equals(itemDto.getItemId()))
                    .findFirst().orElse(null);
            if (item != null && itemDto.getOutQty() != null && itemDto.getOutQty() > 0) {
                sparePartStockMapper.addQuantity(item.getSparePartId(), -itemDto.getOutQty());
            }
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
