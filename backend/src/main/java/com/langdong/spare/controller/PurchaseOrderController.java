package com.langdong.spare.controller;

import com.langdong.spare.dto.AcceptanceDTO;
import com.langdong.spare.dto.PurchaseOrderCreateDTO;
import com.langdong.spare.dto.QuoteDTO;
import com.langdong.spare.entity.PurchaseOrder;
import com.langdong.spare.entity.SupplierQuote;
import com.langdong.spare.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@CrossOrigin(origins = "*")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    private Long getCurrentUserId() {
        return 1L;
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@RequestBody PurchaseOrderCreateDTO dto) {
        return ResponseEntity.ok(purchaseOrderService.create(dto, getCurrentUserId()));
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getList(
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long sparePartId,
            @RequestParam(required = false) Long supplierId) {
        return ResponseEntity.ok(purchaseOrderService.getList(orderStatus, sparePartId, supplierId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getDetail(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam String orderStatus) {
        purchaseOrderService.updateStatus(id, orderStatus);
        return ResponseEntity.ok().build();
    }

    // ---- 询价 ----
    @PostMapping("/{id}/quotes")
    public ResponseEntity<?> addQuote(@PathVariable Long id, @RequestBody QuoteDTO dto) {
        purchaseOrderService.addQuote(id, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/quotes")
    public ResponseEntity<List<SupplierQuote>> getQuotes(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getQuotes(id));
    }

    @PutMapping("/{id}/quotes/{quoteId}/select")
    public ResponseEntity<?> selectQuote(@PathVariable Long id, @PathVariable Long quoteId) {
        purchaseOrderService.selectQuote(id, quoteId);
        return ResponseEntity.ok().build();
    }

    // ---- 验收 ----
    @PutMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @RequestBody AcceptanceDTO dto) {
        purchaseOrderService.accept(id, dto);
        return ResponseEntity.ok().build();
    }
}
