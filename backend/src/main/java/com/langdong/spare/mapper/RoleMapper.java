package com.langdong.spare.mapper;

import com.langdong.spare.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface RoleMapper {
    List<Role> findAll();

    Role findById(Long id);

    Role findByCode(String code);

    int insert(Role role);

    int update(Role role);

    int deleteById(Long id);

    List<Role> findRolesByUserId(Long userId);
}
