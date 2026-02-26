package com.langdong.spare.mapper;

import com.langdong.spare.entity.SupplierCategoryRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SupplierCategoryRelationMapper {
    List<SupplierCategoryRelation> findBySupplierId(Long supplierId);

    int insert(SupplierCategoryRelation relation);

    int deleteBySupplierId(Long supplierId);

    int deleteBySupAndCatId(@Param("supplierId") Long supplierId, @Param("categoryId") Long categoryId);
}
