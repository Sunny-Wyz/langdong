package com.langdong.spare.controller;

import com.langdong.spare.entity.SparePartStock;
import com.langdong.spare.entity.SparePartLocationStock;
import com.langdong.spare.mapper.SparePartLocationStockMapper;
import com.langdong.spare.mapper.SparePartStockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-ledger")
@CrossOrigin(origins = "*")
public class StockLedgerController {

    @Autowired
    private SparePartStockMapper sparePartStockMapper;

    @Autowired
    private SparePartLocationStockMapper sparePartLocationStockMapper;

    @GetMapping
    public ResponseEntity<List<SparePartStock>> getAll() {
        return ResponseEntity.ok(sparePartStockMapper.findAll());
    }

    @GetMapping("/locations")
    public ResponseEntity<List<SparePartLocationStock>> getLocationStocks() {
        return ResponseEntity.ok(sparePartLocationStockMapper.findAllWithDetails());
    }
}
