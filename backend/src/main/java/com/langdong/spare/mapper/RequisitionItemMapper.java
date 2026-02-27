package com.langdong.spare.mapper;

import com.langdong.spare.entity.RequisitionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RequisitionItemMapper {

    int insertBatch(@Param("items") List<RequisitionItem> items);

    /** 获取某个领用单下的所有明细，且带备件名称和编码联表 */
    List<RequisitionItem> findByReqId(Long reqId);

    int updateOutbound(@Param("id") Long id, @Param("outQty") Integer outQty);

    int updateInstallInfo(@Param("id") Long id, @Param("installerId") Long installerId,
            @Param("installLoc") String installLoc);

}
