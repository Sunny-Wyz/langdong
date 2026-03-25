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
        users.forEach(u -> u.setPassword(null));
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:user:list')")
    @Transactional
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String name = (String) body.get("name");
        String rawPassword = (String) body.get("password");
        Integer status = body.containsKey("status") ? (Integer) body.get("status") : 1;
        List<?> roleIds = (List<?>) body.get("roleIds");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body("用户名不能为空");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("密码不能为空");
        }
        if (rawPassword.length() < 6) {
            return ResponseEntity.badRequest().body("密码长度不能少于6位");
        }
        if (userMapper.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setName(name);
        user.setStatus(status);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.insert(user);

        if (roleIds != null && !roleIds.isEmpty()) {
            for (Object roleIdObj : roleIds) {
                userRoleMapper.insert(user.getId(), ((Number) roleIdObj).longValue());
            }
        }

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:list')")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
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
    public ResponseEntity<?> assignRoles(@PathVariable Long id, @RequestBody Map<String, List<?>> body) {
        List<?> roleIds = body.get("roleIds");
        userRoleMapper.deleteByUserId(id);
        if (roleIds != null && !roleIds.isEmpty()) {
            for (Object roleIdObj : roleIds) {
                userRoleMapper.insert(id, ((Number) roleIdObj).longValue());
            }
        }
        return ResponseEntity.ok().build();
    }
}
