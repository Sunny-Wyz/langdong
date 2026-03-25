package com.langdong.spare.controller;

import com.langdong.spare.dto.StockInRequestDTO;
import com.langdong.spare.entity.PurchaseOrderItem;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.service.StockInService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/po/{poCode}")
    @PreAuthorize("hasAuthority('po:receive:confirm')")
    public ResponseEntity<List<PurchaseOrderItem>> getPendingItems(@PathVariable String poCode) {
        try {
            List<PurchaseOrderItem> items = stockInService.getPendingItems(poCode);
            return ResponseEntity.ok(items);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('po:receive:confirm')")
    public ResponseEntity<?> createStockIn(@RequestBody StockInRequestDTO request) {
        try {
            stockInService.createStockIn(request, getCurrentUserId());
            return ResponseEntity.ok("入库成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("入库操作失败，请检查数据后重试");
        }
    }
}
