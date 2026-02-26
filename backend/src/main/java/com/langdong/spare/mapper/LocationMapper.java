package com.langdong.spare.mapper;

import com.langdong.spare.entity.Location;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LocationMapper {
    List<Location> findAll();

    Location findById(Long id);

    int insert(Location location);

    int update(Location location);

    int deleteById(Long id);
}
