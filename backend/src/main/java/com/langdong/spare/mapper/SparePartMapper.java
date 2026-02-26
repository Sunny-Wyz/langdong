package com.langdong.spare.mapper;

import com.langdong.spare.entity.SparePart;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SparePartMapper {
    List<SparePart> findAll();

    SparePart findById(Long id);

    int insert(SparePart sparePart);

    int update(SparePart sparePart);

    int deleteById(Long id);
}
