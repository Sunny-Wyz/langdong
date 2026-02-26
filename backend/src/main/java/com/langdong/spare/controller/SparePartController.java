package com.langdong.spare.controller;

import com.langdong.spare.dto.SparePartDTO;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.entity.SparePartCategory;
import com.langdong.spare.mapper.SparePartCategoryMapper;
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

    @Autowired
    private SparePartCategoryMapper categoryMapper;

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
        if (dto.getCategoryId() == null) {
            return ResponseEntity.badRequest().body("分类不能为空");
        }

        // 自动发号逻辑
        SparePartCategory categoryInfo = categoryMapper.findById(dto.getCategoryId());
        if (categoryInfo == null) {
            return ResponseEntity.badRequest().body("选择的分类不存在");
        }
        String prefix = categoryInfo.getCode(); // 分类编码为前4位
        String maxCode = sparePartMapper.findMaxCodeByPrefix(prefix);
        int nextNum = 1;
        if (maxCode != null && maxCode.length() == 8) {
            String seqStr = maxCode.substring(4);
            try {
                nextNum = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        String generatedCode = prefix + String.format("%04d", nextNum);

        SparePart sparePart = new SparePart();
        BeanUtils.copyProperties(dto, sparePart);
        sparePart.setCode(generatedCode);
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
        if (dto.getCategoryId() == null) {
            return ResponseEntity.badRequest().body("分类不能为空");
        }
        SparePart existing = sparePartMapper.findById(id);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        SparePart sparePart = new SparePart();
        BeanUtils.copyProperties(dto, sparePart);
        sparePart.setId(id);
        sparePart.setCode(existing.getCode()); // 保护已有编号不被篡改
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
