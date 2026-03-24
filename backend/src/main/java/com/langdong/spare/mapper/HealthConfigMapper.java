package com.langdong.spare.mapper;

import com.langdong.spare.entity.HealthConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 设备健康阈值配置 Mapper
 * 对应数据库表：sys_device_health_config
 */
@Mapper
public interface HealthConfigMapper {

    /**
     * 插入配置记录
     *
     * @param config 配置记录
     * @return 影响行数
     */
    int insert(HealthConfig config);

    /**
     * 更新配置记录
     *
     * @param config 配置记录
     * @return 影响行数
     */
    int update(HealthConfig config);

    /**
     * 根据ID查询配置
     *
     * @param id 配置ID
     * @return 配置记录
     */
    HealthConfig findById(@Param("id") Long id);

    /**
     * 根据设备类型和重要性查询配置
     * 优先级：精确匹配 > 仅类型匹配 > 仅重要性匹配 > 全局默认
     *
     * @param deviceType      设备类型（null=全局）
     * @param importanceLevel 重要性（null=全局）
     * @return 配置记录（返回最匹配的一条）
     */
    HealthConfig findByDeviceTypeAndImportance(@Param("deviceType") String deviceType,
                                                @Param("importanceLevel") String importanceLevel);

    /**
     * 查询全局默认配置（deviceType=null AND importanceLevel=null）
     *
     * @return 全局默认配置
     */
    HealthConfig findGlobalDefault();

    /**
     * 查询所有配置（按优先级排序：全局默认在最后）
     *
     * @return 配置记录列表
     */
    List<HealthConfig> findAll();

    /**
     * 根据设备类型查询配置列表
     *
     * @param deviceType 设备类型
     * @return 配置记录列表
     */
    List<HealthConfig> findByDeviceType(@Param("deviceType") String deviceType);

    /**
     * 根据重要性查询配置列表
     *
     * @param importanceLevel 重要性
     * @return 配置记录列表
     */
    List<HealthConfig> findByImportance(@Param("importanceLevel") String importanceLevel);

    /**
     * 删除配置记录
     *
     * @param id 配置ID
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 检查是否存在指定设备类型和重要性的配置
     *
     * @param deviceType      设备类型
     * @param importanceLevel 重要性
     * @return 存在返回>0，不存在返回0
     */
    long countByDeviceTypeAndImportance(@Param("deviceType") String deviceType,
                                         @Param("importanceLevel") String importanceLevel);
}
