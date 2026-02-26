package com.langdong.spare.controller;

import com.langdong.spare.entity.Supplier;
import com.langdong.spare.entity.SupplierCategoryRelation;
import com.langdong.spare.entity.SupplyCategory;
import com.langdong.spare.mapper.SupplierCategoryRelationMapper;
import com.langdong.spare.mapper.SupplierMapper;
import com.langdong.spare.mapper.SupplyCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*")
public class SupplierController {

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private SupplierCategoryRelationMapper relationMapper;

    @Autowired
    private SupplyCategoryMapper categoryMapper;

    @GetMapping
    public List<Supplier> getAll() {
        return supplierMapper.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getById(@PathVariable Long id) {
        Supplier supplier = supplierMapper.findById(id);
        if (supplier == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(supplier);
    }

    @PostMapping
    public Supplier create(@RequestBody Supplier supplier) {
        supplierMapper.insert(supplier);
        return supplier;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(@PathVariable Long id, @RequestBody Supplier supplier) {
        Supplier existing = supplierMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        supplier.setId(id);
        supplierMapper.update(supplier);
        return ResponseEntity.ok(supplier);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Supplier existing = supplierMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        relationMapper.deleteBySupplierId(id);
        supplierMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // --- 供货品类关联 ---

    @GetMapping("/{id}/categories")
    public ResponseEntity<List<SupplyCategory>> getSupplierCategories(@PathVariable Long id) {
        Supplier existing = supplierMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        List<SupplierCategoryRelation> relations = relationMapper.findBySupplierId(id);
        List<Long> categoryIds = relations.stream().map(SupplierCategoryRelation::getSupplyCategoryId)
                .collect(Collectors.toList());

        List<SupplyCategory> allCategories = categoryMapper.findAll();
        List<SupplyCategory> linked = allCategories.stream()
                .filter(c -> categoryIds.contains(c.getId()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(linked);
    }

    @PostMapping("/{id}/categories")
    public ResponseEntity<?> linkCategories(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        Supplier existing = supplierMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        List<Long> categoryIds = body.get("categoryIds");
        if (categoryIds == null) {
            return ResponseEntity.badRequest().build();
        }

        // 全量替换：先删后加
        relationMapper.deleteBySupplierId(id);
        for (Long catId : categoryIds) {
            SupplierCategoryRelation rel = new SupplierCategoryRelation();
            rel.setSupplierId(id);
            rel.setSupplyCategoryId(catId);
            relationMapper.insert(rel);
        }
        return ResponseEntity.ok().build();
    }
}
