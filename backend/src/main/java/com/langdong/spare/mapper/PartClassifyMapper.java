package com.langdong.spare.mapper;

import com.langdong.spare.entity.PartClassify;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 备件分类结果 Mapper
 * 对应数据库表：biz_part_classify
 */
@Mapper
public interface PartClassifyMapper {

    /**
     * 批量插入本次重算结果
     * 每次全量重算插入新记录，不更新旧记录
     *
     * @param list 分类结果列表
     */
    int insertBatch(@Param("list") List<PartClassify> list);

    /**
     * 分页查询最新月份的分类结果
     * 联查 spare_part 表获取备件名称
     *
     * @param abcClass  ABC分类过滤（null=全部）
     * @param xyzClass  XYZ分类过滤（null=全部）
     * @param partCode  备件编码关键词（null=全部，模糊匹配）
     * @param month     分类月份（null=查最新月份）
     * @param offset    分页偏移量
     * @param size      每页大小
     */
    List<PartClassify> findLatestByPage(
            @Param("abcClass") String abcClass,
            @Param("xyzClass") String xyzClass,
            @Param("partCode") String partCode,
            @Param("month") String month,
            @Param("offset") int offset,
            @Param("size") int size
    );

    /**
     * 查询分页总记录数（配合 findLatestByPage 使用）
     */
    long countLatest(
            @Param("abcClass") String abcClass,
            @Param("xyzClass") String xyzClass,
            @Param("partCode") String partCode,
            @Param("month") String month
    );

    /**
     * 查询指定备件的全部历史分类记录（按月份升序）
     *
     * @param partCode 备件编码
     */
    List<PartClassify> findHistoryByPartCode(@Param("partCode") String partCode);

    /**
     * 统计 ABC×XYZ 9格矩阵中每格的备件数量
     * 仅统计最新月份的数据
     * 返回格式：[{abcClass: "A", xyzClass: "X", cnt: 12}, ...]
     */
    List<Map<String, Object>> findMatrixCount();

    /**
     * 查询最新的分类月份（classify_month 最大值）
     */
    String findLatestMonth();
}
