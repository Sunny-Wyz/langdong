package com.langdong.spare.controller;

import com.langdong.spare.dto.StockInRequestDTO;
import com.langdong.spare.entity.PurchaseOrder;
import com.langdong.spare.entity.PurchaseOrderItem;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.service.StockInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-in")
public class StockInController {

    @Autowired
    private StockInService stockInService;

    @Autowired
    private UserMapper userMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : null;
    }

    /** 可收货采购单列表 */
    @GetMapping("/receivable-orders")
    @PreAuthorize("hasAnyAuthority('po:receive:confirm','wh:stockin:list')")
    public ResponseEntity<?> listReceivableOrders() {
        List<PurchaseOrder> list = stockInService.listReceivableOrders();
        Map<String, Object> body = new HashMap<>();
        body.put("code", 200);
        body.put("data", list);
        body.put("total", list.size());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/po/{poCode}")
    @PreAuthorize("hasAnyAuthority('po:receive:confirm','wh:stockin:list')")
    public ResponseEntity<?> getPendingItems(@PathVariable String poCode) {
        try {
            List<PurchaseOrderItem> items = stockInService.getPendingItems(poCode);
            return ResponseEntity.ok(items);
        } catch (RuntimeException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 400);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('po:receive:confirm','wh:stockin:list')")
    public ResponseEntity<?> createStockIn(@RequestBody StockInRequestDTO request) {
        try {
            stockInService.createStockIn(request, getCurrentUserId());
            Map<String, Object> ok = new HashMap<>();
            ok.put("code", 200);
            ok.put("message", "入库成功");
            return ResponseEntity.ok(ok);
        } catch (RuntimeException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("code", 400);
            err.put("message", e.getMessage() != null ? e.getMessage() : "入库操作失败，请检查数据后重试");
            return ResponseEntity.badRequest().body(err);
        }
    }
}
