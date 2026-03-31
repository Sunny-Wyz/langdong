package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiTaskResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiTaskResultMapper {

    AiTaskResult findByTaskId(@Param("taskId") String taskId);

    void insertOrUpdate(AiTaskResult record);

    void deleteOlderThan(@Param("days") int days);
}
