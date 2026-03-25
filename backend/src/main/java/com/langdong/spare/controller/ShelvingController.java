package com.langdong.spare.controller;

import com.langdong.spare.dto.ShelvingSubmitDTO;
import com.langdong.spare.entity.StockInItem;
import com.langdong.spare.service.ShelvingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shelving")
public class ShelvingController {

    @Autowired
    private ShelvingService shelvingService;

    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('po:receive:confirm')")
    public ResponseEntity<List<StockInItem>> getPendingShelvingItems() {
        return ResponseEntity.ok(shelvingService.getPendingShelvingItems());
    }

    @PostMapping("/submit")
    @PreAuthorize("hasAuthority('po:receive:confirm')")
    public ResponseEntity<String> submitShelving(@RequestBody List<ShelvingSubmitDTO> requests) {
        try {
            shelvingService.submitShelving(requests);
            return ResponseEntity.ok("上架成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("上架操作失败，请检查数据后重试");
        }
    }
}
