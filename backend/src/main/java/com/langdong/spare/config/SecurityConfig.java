package com.langdong.spare.config;

import com.langdong.spare.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
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

    @Value("${app.cors.allowed-origin:http://localhost:3000}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil,
            UserMapper userMapper, MenuMapper menuMapper) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/ai/forecast/callback/python/replenishment").permitAll()
                    .requestMatchers("/internal/ai/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter(jwtUtil, userMapper, menuMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private OncePerRequestFilter jwtFilter(JwtUtil jwtUtil, UserMapper userMapper, MenuMapper menuMapper) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                    HttpServletResponse res,
                    FilterChain chain) throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                    String token = header.substring(7);
                    if (jwtUtil.validate(token)) {
                        String username = jwtUtil.getUsername(token);
                        User user = userMapper.findByUsername(username);
                        if (user != null && user.getStatus() == 1) {
                            List<Menu> menus = menuMapper.findMenusByUserId(user.getId());
                            List<SimpleGrantedAuthority> authorities = menus.stream()
                                    .filter(m -> m.getPermission() != null && !m.getPermission().isEmpty())
                                    .map(m -> new SimpleGrantedAuthority(m.getPermission()))
                                    .toList();
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(username,
                                    null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }
}
