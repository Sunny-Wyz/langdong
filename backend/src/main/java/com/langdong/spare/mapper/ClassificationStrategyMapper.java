package com.langdong.spare.mapper;

import com.langdong.spare.entity.ClassificationStrategy;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ClassificationStrategyMapper {
    List<ClassificationStrategy> findAll();
    ClassificationStrategy findByCombinationCode(String combinationCode);
    void insert(ClassificationStrategy strategy);
    void update(ClassificationStrategy strategy);
    void deleteById(Long id);
}
