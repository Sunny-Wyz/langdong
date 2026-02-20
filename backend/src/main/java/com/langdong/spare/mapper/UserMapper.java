package com.langdong.spare.mapper;

import com.langdong.spare.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    User findByUsername(String username);
}
