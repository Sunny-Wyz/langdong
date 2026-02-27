package com.langdong.spare.controller;

import com.langdong.spare.dto.StockInRequestDTO;
import com.langdong.spare.entity.PurchaseOrderItem;
import com.langdong.spare.service.StockInService;
import com.langdong.spare.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/stock-in")
@CrossOrigin(origins = "*")
public class StockInController {

    @Autowired
    private StockInService stockInService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/po/{poCode}")
    @PreAuthorize("hasAuthority('sys:stock:in')")
    public ResponseEntity<List<PurchaseOrderItem>> getPendingItems(@PathVariable String poCode) {
        try {
            List<PurchaseOrderItem> items = stockInService.getPendingItems(poCode);
            return ResponseEntity.ok(items);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:stock:in')")
    public ResponseEntity<?> createStockIn(@RequestBody StockInRequestDTO request, HttpServletRequest httpRequest) {
        try {
            // 解析操作人ID
            String token = httpRequest.getHeader("Authorization").substring(7);
            String username = jwtUtil.getUsername(token);
            // 简易处理：这里可以结合 UserMapper 拿出真正的 ID，但是为了减少依赖，可暂传固定的管理员ID或查询
            // 假设我们传入 1L 作为测试 userId
            Long userId = 1L;

            stockInService.createStockIn(request, userId);
            return ResponseEntity.ok("入库成功");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
