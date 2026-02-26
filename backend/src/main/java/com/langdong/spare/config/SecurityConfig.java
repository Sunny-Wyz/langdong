package com.langdong.spare.config;

import com.langdong.spare.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.langdong.spare.entity.User;
import com.langdong.spare.entity.Menu;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.mapper.MenuMapper;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, UserMapper userMapper, MenuMapper menuMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configure(http)) // 启用默认 CORS 配置
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.web.bind.annotation.RequestMethod.OPTIONS.name()).permitAll() // 尝试放行所有 OPTIONS
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                        String path = req.getRequestURI();
                        // 调试日志：打印所有进入过滤器的请求路径
                        if (path.startsWith("/api")) {
                            System.out.println("Spring Security 拦截到请求: " + path);
                        }
                        
                        String header = req.getHeader("Authorization");
                        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                            String token = header.substring(7);
                            if (jwtUtil.validate(token)) {
                                String username = jwtUtil.getUsername(token);
                                com.langdong.spare.entity.User user = userMapper.findByUsername(username);
                                if (user != null && user.getStatus() == 1) {
                                    List<com.langdong.spare.entity.Menu> menus = menuMapper.findMenusByUserId(user.getId());
                                    List<SimpleGrantedAuthority> authorities = menus.stream()
                                            .filter(m -> m.getPermission() != null && !m.getPermission().isEmpty())
                                            .map(m -> new SimpleGrantedAuthority(m.getPermission()))
                                            .toList();
                                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                                    SecurityContextHolder.getContext().setAuthentication(auth);
                                }
                            }
                        }
                        chain.doFilter(req, res);
                    }
                }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
