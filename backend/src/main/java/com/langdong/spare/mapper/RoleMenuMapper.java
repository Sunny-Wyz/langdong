package com.langdong.spare.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMenuMapper {
    int insert(@Param("roleId") Long roleId, @Param("menuId") Long menuId);

    int deleteByRoleId(Long roleId);

    int deleteByMenuId(Long menuId);

    List<Long> findMenuIdsByRoleId(Long roleId);
}
