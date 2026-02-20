package com.langdong.spare.controller;

import com.langdong.spare.dto.LoginRequest;
import com.langdong.spare.dto.LoginResponse;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userMapper.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "用户名或密码错误"));
        }
        String token = jwtUtil.generate(user.getUsername());
        return ResponseEntity.ok(new LoginResponse(token, user.getUsername()));
    }
}
