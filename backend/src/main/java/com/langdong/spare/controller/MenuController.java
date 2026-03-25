package com.langdong.spare.controller;

import com.langdong.spare.entity.Menu;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.MenuMapper;
import com.langdong.spare.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

// 动态路由与权限菜单字典加载核心控制器
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    @Autowired
    private MenuMapper menuMapper;

    @Autowired
    private UserMapper userMapper;

    // [核心] 获取当前登录用户的动态路由和权限树，用于前端侧边栏渲染与按钮鉴权
    @GetMapping("/my")
    public ResponseEntity<List<Menu>> getMyMenus() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        List<Menu> menus = menuMapper.findMenusByUserId(user.getId());
        List<Menu> tree = buildTree(menus, null);
        return ResponseEntity.ok(tree);
    }

    // [核心] 获取系统全量菜单和权限定义树，用于管理员在建立角色时进行全量勾选分配
    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<List<Menu>> getAllMenus() {
        List<Menu> menus = menuMapper.findAll();
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
