package com.langdong.spare.controller;

import com.langdong.spare.dto.AcceptanceDTO;
import com.langdong.spare.dto.PurchaseOrderCreateDTO;
import com.langdong.spare.dto.QuoteDTO;
import com.langdong.spare.entity.PurchaseOrder;
import com.langdong.spare.entity.SupplierQuote;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private UserMapper userMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : null;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('po:order:add')")
    public ResponseEntity<PurchaseOrder> create(@RequestBody PurchaseOrderCreateDTO dto) {
        return ResponseEntity.ok(purchaseOrderService.create(dto, getCurrentUserId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('po:order:list')")
    public ResponseEntity<List<PurchaseOrder>> getList(
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long sparePartId,
            @RequestParam(required = false) Long supplierId) {
        return ResponseEntity.ok(purchaseOrderService.getList(orderStatus, sparePartId, supplierId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('po:order:list')")
    public ResponseEntity<PurchaseOrder> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getDetail(id));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('po:order:add')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam String orderStatus) {
        purchaseOrderService.updateStatus(id, orderStatus);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/quotes")
    @PreAuthorize("hasAuthority('po:quote:edit')")
    public ResponseEntity<?> addQuote(@PathVariable Long id, @RequestBody QuoteDTO dto) {
        purchaseOrderService.addQuote(id, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/quotes")
    @PreAuthorize("hasAuthority('po:order:list')")
    public ResponseEntity<List<SupplierQuote>> getQuotes(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getQuotes(id));
    }

    @PutMapping("/{id}/quotes/{quoteId}/select")
    @PreAuthorize("hasAuthority('po:quote:edit')")
    public ResponseEntity<?> selectQuote(@PathVariable Long id, @PathVariable Long quoteId) {
        purchaseOrderService.selectQuote(id, quoteId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('po:receive:confirm')")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestBody AcceptanceDTO dto) {
        purchaseOrderService.accept(id, dto);
        return ResponseEntity.ok().build();
    }
}
