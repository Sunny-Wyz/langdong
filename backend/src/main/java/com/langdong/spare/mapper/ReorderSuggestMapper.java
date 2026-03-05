package com.langdong.spare.mapper;

import com.langdong.spare.entity.ReorderSuggest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReorderSuggestMapper {
    List<ReorderSuggest> findByStatus(@Param("status") String status);

    long countByStatus(@Param("status") String status);

    Integer findCurrentStockByPartCode(@Param("partCode") String partCode);

    void deletePendingByPartAndMonth(@Param("partCode") String partCode,
                                     @Param("suggestMonth") String suggestMonth);

    int insert(ReorderSuggest suggest);

    int bootstrapPendingSuggestions(@Param("suggestMonth") String suggestMonth);

    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
