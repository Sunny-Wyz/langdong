package com.langdong.spare.mapper;

import com.langdong.spare.entity.ClassificationAdjustmentRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ClassificationAdjustmentRecordMapper {
    List<ClassificationAdjustmentRecord> findAll();
    List<ClassificationAdjustmentRecord> findByStatus(String status);
    ClassificationAdjustmentRecord findById(Long id);
    void insert(ClassificationAdjustmentRecord record);
    void updateStatus(ClassificationAdjustmentRecord record);
}
