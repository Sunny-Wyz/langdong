package com.langdong.spare.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserRoleMapper {
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteByUserId(Long userId);

    int deleteByRoleId(Long roleId);

    List<Long> findRoleIdsByUserId(Long userId);
}
