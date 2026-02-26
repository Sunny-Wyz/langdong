package com.langdong.spare.controller;

import com.langdong.spare.entity.SparePartCategory;
import com.langdong.spare.mapper.SparePartCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spare-categories")
@CrossOrigin(origins = "*")
public class SparePartCategoryController {

    @Autowired
    private SparePartCategoryMapper categoryMapper;

    @GetMapping
    public List<SparePartCategory> getAll() {
        return categoryMapper.findAll();
    }
}
