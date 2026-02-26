package com.langdong.spare.mapper;

import com.langdong.spare.entity.SparePartCategory;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SparePartCategoryMapper {
    List<SparePartCategory> findAll();

    SparePartCategory findById(Long id);

    SparePartCategory findByCode(String code);
}
