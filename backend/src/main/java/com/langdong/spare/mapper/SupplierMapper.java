package com.langdong.spare.mapper;

import com.langdong.spare.entity.Supplier;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SupplierMapper {
    List<Supplier> findAll();

    Supplier findById(Long id);

    int insert(Supplier supplier);

    int update(Supplier supplier);

    int deleteById(Long id);
}
