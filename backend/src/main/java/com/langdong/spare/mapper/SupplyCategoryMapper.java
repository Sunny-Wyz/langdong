package com.langdong.spare.mapper;

import com.langdong.spare.entity.SupplyCategory;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SupplyCategoryMapper {
    List<SupplyCategory> findAll();

    SupplyCategory findById(Long id);

    int insert(SupplyCategory category);

    int update(SupplyCategory category);

    int deleteById(Long id);
}
