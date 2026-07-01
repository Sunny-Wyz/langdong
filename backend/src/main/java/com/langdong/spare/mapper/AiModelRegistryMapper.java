package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiModelRegistry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 模型注册表 Mapper
 * 对应表：ai_model_registry
 */
@Mapper
public interface AiModelRegistryMapper {

    /**
     * 插入模型注册记录
     */
    int insert(AiModelRegistry registry);

    /**
     * 根据模型名称查询当前处于生产状态（PRODUCTION）的模型快照
     */
    AiModelRegistry findProductionModel(@Param("modelName") String modelName);

    /**
     * 更新指定模型版本记录的状态
     */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * 根据模型名称查询所有历史版本，按创建时间降序
     */
    List<AiModelRegistry> findHistoryByName(@Param("modelName") String modelName);

    /**
     * 根据特定版本号查询模型快照
     */
    AiModelRegistry findByVersion(@Param("version") String version);
}
