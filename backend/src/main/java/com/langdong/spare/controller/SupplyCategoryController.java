package com.langdong.spare.controller;

import com.langdong.spare.entity.SupplyCategory;
import com.langdong.spare.mapper.SupplyCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/supply-categories")
public class SupplyCategoryController {

    @Autowired
    private SupplyCategoryMapper supplyCategoryMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('base:category:list')")
    public List<SupplyCategory> getAll() {
        return supplyCategoryMapper.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:category:list')")
    public SupplyCategory create(@RequestBody SupplyCategory category) {
        supplyCategoryMapper.insert(category);
        return category;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:category:list')")
    public ResponseEntity<SupplyCategory> update(@PathVariable Long id, @RequestBody SupplyCategory category) {
        SupplyCategory existing = supplyCategoryMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        category.setId(id);
        supplyCategoryMapper.update(category);
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:category:list')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        SupplyCategory existing = supplyCategoryMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        supplyCategoryMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
