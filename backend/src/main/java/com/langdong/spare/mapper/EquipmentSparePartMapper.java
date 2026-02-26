package com.langdong.spare.mapper;

import com.langdong.spare.entity.EquipmentSparePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EquipmentSparePartMapper {
    List<EquipmentSparePart> findByEquipmentId(Long equipmentId);

    List<EquipmentSparePart> findBySparePartId(Long sparePartId);

    EquipmentSparePart findByEqAndSpId(@Param("equipmentId") Long equipmentId, @Param("sparePartId") Long sparePartId);

    int insert(EquipmentSparePart equipmentSparePart);

    int updateQuantity(EquipmentSparePart equipmentSparePart);

    int deleteById(Long id);

    int deleteByEquipmentId(Long equipmentId);

    int deleteByEqAndSpId(@Param("equipmentId") Long equipmentId, @Param("sparePartId") Long sparePartId);
}
