package com.langdong.spare.controller;

import com.langdong.spare.dto.SparePartDTO;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spare-parts")
@CrossOrigin(origins = "*")
public class SparePartController {

    @Autowired
    private SparePartMapper sparePartMapper;

    @GetMapping
    public ResponseEntity<List<SparePart>> list() {
        return ResponseEntity.ok(sparePartMapper.findAll());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SparePartDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body("备件名称不能为空");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 0) {
            return ResponseEntity.badRequest().body("库存数量不能为负数");
        }
        SparePart sparePart = new SparePart();
        BeanUtils.copyProperties(dto, sparePart);
        sparePartMapper.insert(sparePart);
        return ResponseEntity.ok(sparePart);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SparePartDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body("备件名称不能为空");
        }
        if (dto.getQuantity() == null || dto.getQuantity() < 0) {
            return ResponseEntity.badRequest().body("库存数量不能为负数");
        }
        SparePart existing = sparePartMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SparePart sparePart = new SparePart();
        BeanUtils.copyProperties(dto, sparePart);
        sparePart.setId(id);
        sparePartMapper.update(sparePart);
        return ResponseEntity.ok(sparePart);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        int rows = sparePartMapper.deleteById(id);
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
