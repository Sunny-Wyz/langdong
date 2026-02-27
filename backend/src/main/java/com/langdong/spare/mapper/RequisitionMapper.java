package com.langdong.spare.mapper;

import com.langdong.spare.entity.Requisition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RequisitionMapper {

    int insert(Requisition requisition);

    Requisition findById(Long id);

    /** 查询带有人员/设备名称联表详情的工单信息 */
    Requisition findWithDetailsById(Long id);

    /** 支持按状态、提单人多条件过滤查询，带联表信息 */
    List<Requisition> findList(@Param("status") String status, @Param("applicantId") Long applicantId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateApprovalInfo(@Param("id") Long id, @Param("approveId") Long approveId,
            @Param("approveRemark") String approveRemark, @Param("status") String status);
}
