package com.langdong.spare.controller;

import com.langdong.spare.dto.SparePartDTO;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.entity.SparePartCategory;
import com.langdong.spare.mapper.SparePartCategoryMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spare-parts")
public class SparePartController {

    @Autowired
    private SparePartMapper sparePartMapper;

    @Autowired
    private SparePartCategoryMapper categoryMapper;

    @Autowired
    private com.langdong.spare.service.SparePartService sparePartService;

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:spare:add')")
    public ResponseEntity<?> importData(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请选择要导入的文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body("文件大小不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body("仅支持.xlsx格式的Excel文件");
        }
        try {
            Map<String, Object> result = sparePartService.importSpareParts(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("导入失败，请检查文件格式是否正确");
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:spare:list')")
    public ResponseEntity<List<SparePart>> list() {
        return ResponseEntity.ok(sparePartMapper.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:spare:add')")
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

        SparePartCategory categoryInfo = categoryMapper.findById(dto.getCategoryId());
        if (categoryInfo == null) {
            return ResponseEntity.badRequest().body("选择的分类不存在");
        }
        String prefix = categoryInfo.getCode();
        int prefixLen = prefix.length();
        String maxCode = sparePartMapper.findMaxCodeByPrefix(prefix);
        int nextNum = 1;
        if (maxCode != null && maxCode.length() == prefixLen + 4) {
            try {
                nextNum = Integer.parseInt(maxCode.substring(prefixLen)) + 1;
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
    @PreAuthorize("hasAuthority('base:spare:add')")
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
        sparePart.setCode(existing.getCode());
        sparePartMapper.update(sparePart);
        return ResponseEntity.ok(sparePart);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:spare:add')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        int rows = sparePartMapper.deleteById(id);
        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }
}
