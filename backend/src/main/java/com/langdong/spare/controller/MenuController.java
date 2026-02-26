package com.langdong.spare.controller;

import com.langdong.spare.entity.Menu;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.MenuMapper;
import com.langdong.spare.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/menus")
@CrossOrigin(origins = "*")
public class MenuController {

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/my")
    public ResponseEntity<List<Menu>> getMyMenus() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<Menu> menus = menuMapper.findMenusByUserId(user.getId());
        // 构建树形结构
        List<Menu> tree = buildTree(menus, null);
        return ResponseEntity.ok(tree);
    }

    @GetMapping
    public ResponseEntity<List<Menu>> getAllMenus() {
        List<Menu> menus = menuMapper.findAll();
        // 构建树形结构返回给分配树
        return ResponseEntity.ok(buildTree(menus, null));
    }

    private List<Menu> buildTree(List<Menu> list, Long parentId) {
        return list.stream()
                .filter(m -> (parentId == null && m.getParentId() == null)
                        || (parentId != null && parentId.equals(m.getParentId())))
                .map(m -> {
                    m.setChildren(buildTree(list, m.getId()));
                    return m;
                })
                .collect(Collectors.toList());
    }
}
