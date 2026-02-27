package com.langdong.spare.mapper;

import com.langdong.spare.entity.SparePartLocationStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SparePartLocationStockMapper {
    /** 查询所有带详情的货位库存 */
    List<SparePartLocationStock> findAllWithDetails();

    /** 查询某货位上的某个备件数量 */
    SparePartLocationStock findByLocationAndSparePart(@Param("locationId") Long locationId,
            @Param("sparePartId") Long sparePartId);

    /** 查询某货位上存储的所有物品总数（用于容量校验） */
    Integer sumQuantityByLocationId(@Param("locationId") Long locationId);

    /** 新增货位库存记录 */
    int insert(SparePartLocationStock stock);

    /** 累加货位上的指定备件数量 */
    int addQuantity(@Param("locationId") Long locationId, @Param("sparePartId") Long sparePartId,
            @Param("addedQuantity") Integer addedQuantity);
}
