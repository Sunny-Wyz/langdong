package com.langdong.spare.mapper;

import com.langdong.spare.entity.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface UserMapper {
    User findByUsername(String username);

    List<User> findAll();

    int insert(User user);

    int update(User user);

    int deleteById(Long id);
}
