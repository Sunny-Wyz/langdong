package com.langdong.spare.controller;

import com.langdong.spare.entity.Role;
import com.langdong.spare.mapper.RoleMapper;
import com.langdong.spare.mapper.RoleMenuMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 角色与权限关联管理核心控制器
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RoleMenuMapper roleMenuMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<List<Role>> list() {
        return ResponseEntity.ok(roleMapper.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<Role> create(@RequestBody Role role) {
        roleMapper.insert(role);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<Role> update(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        roleMapper.update(role);
        return ResponseEntity.ok(role);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        roleMenuMapper.deleteByRoleId(id);
        roleMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // [核心] 获取当前角色已分配的菜单/权限ID列表，用于前端树形控件回显
    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('sys:role:list')")
    public ResponseEntity<List<Long>> getRoleMenus(@PathVariable Long id) {
        return ResponseEntity.ok(roleMenuMapper.findMenuIdsByRoleId(id));
    }

    // [核心] 为角色分配菜单和按钮权限 (全量覆盖：先删除旧关联再插入新关联)
    @PostMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('sys:role:list')")
    @Transactional
    public ResponseEntity<?> assignMenus(@PathVariable Long id, @RequestBody Map<String, List<?>> body) {
        List<?> menuIds = body.get("menuIds");
        roleMenuMapper.deleteByRoleId(id);
        if (menuIds != null && !menuIds.isEmpty()) {
            for (Object menuIdObj : menuIds) {
                roleMenuMapper.insert(id, ((Number) menuIdObj).longValue());
            }
        }
        return ResponseEntity.ok().build();
    }
}
