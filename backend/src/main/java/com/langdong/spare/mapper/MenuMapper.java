package com.langdong.spare.mapper;

import com.langdong.spare.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MenuMapper {
    List<Menu> findAll();

    Menu findById(Long id);

    int insert(Menu menu);

    int update(Menu menu);

    int deleteById(Long id);

    List<Menu> findMenusByUserId(Long userId);
}
