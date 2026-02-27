package com.langdong.spare.controller;

import com.langdong.spare.dto.ShelvingSubmitDTO;
import com.langdong.spare.entity.StockInItem;
import com.langdong.spare.service.ShelvingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shelving")
@CrossOrigin(origins = "*")
public class ShelvingController {

    @Autowired
    private ShelvingService shelvingService;

    @GetMapping("/pending")
    public ResponseEntity<List<StockInItem>> getPendingShelvingItems() {
        return ResponseEntity.ok(shelvingService.getPendingShelvingItems());
    }

    @PostMapping("/submit")
    public ResponseEntity<String> submitShelving(@RequestBody List<ShelvingSubmitDTO> requests) {
        try {
            shelvingService.submitShelving(requests);
            return ResponseEntity.ok("上架成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
