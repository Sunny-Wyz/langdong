package com.langdong.spare.controller;

import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<List<User>> list() {
        List<User> users = userMapper.findAll();
        users.forEach(u -> u.setPassword(null)); // 隐藏密码
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<?> create(@RequestBody User user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode("123456")); // 默认密码
        userMapper.insert(user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 不更新密码
        }
        userMapper.update(user);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        userRoleMapper.deleteByUserId(id);
        userMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<List<Long>> getUserRoles(@PathVariable Long id) {
        return ResponseEntity.ok(userRoleMapper.findRoleIdsByUserId(id));
    }

    @PostMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('sys:user:list')")
    @Transactional
    public ResponseEntity<?> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> roleIds = body.get("roleIds");
        userRoleMapper.deleteByUserId(id);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Long roleId : roleIds) {
                userRoleMapper.insert(id, roleId);
            }
        }
        return ResponseEntity.ok().build();
    }
}
