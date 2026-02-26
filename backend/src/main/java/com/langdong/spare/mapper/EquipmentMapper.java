package com.langdong.spare.mapper;

import com.langdong.spare.entity.Equipment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EquipmentMapper {
    List<Equipment> findAll();

    Equipment findById(Long id);

    int insert(Equipment equipment);

    int update(Equipment equipment);

    int deleteById(Long id);
}
