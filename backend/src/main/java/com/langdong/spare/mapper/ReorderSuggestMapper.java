package com.langdong.spare.mapper;

import com.langdong.spare.entity.ReorderSuggest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ReorderSuggestMapper {
    List<ReorderSuggest> findByStatus(@Param("status") String status);

    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
