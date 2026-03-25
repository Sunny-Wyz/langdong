package com.langdong.spare.controller;

import com.langdong.spare.entity.SparePartCategory;
import com.langdong.spare.mapper.SparePartCategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spare-categories")
public class SparePartCategoryController {

    @Autowired
    private SparePartCategoryMapper categoryMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('base:spare:list')")
    public List<SparePartCategory> getAll() {
        return categoryMapper.findAll();
    }
}
