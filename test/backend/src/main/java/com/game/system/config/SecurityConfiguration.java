package com.game.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfiguration Spring Security安全配置类
 * 配置JWT认证、接口权限控制等
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfiguration(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider
    ) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（因为使用JWT，不需要CSRF保护）
                .csrf(AbstractHttpConfigurer::disable)

                // 配置接口权限
                .authorizeHttpRequests(auth -> auth
                        // 公开接口（不需要认证）
                        .requestMatchers(
                                "/api/base/**",           // 健康检查等基础接口
                                "/api/auth/login",        // 登录接口
                                "/api/auth/register",     // 注册接口
                                "/api/auth/captcha",      // 验证码接口
                                "/api/auth/forgot-password" // 忘记密码接口
                        ).permitAll()

                        // 其他所有接口都需要认证
                        .anyRequest().authenticated()
                )

                // 配置Session管理（无状态）
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 配置认证提供者
                .authenticationProvider(authenticationProvider)

                // 添加JWT过滤器（在UsernamePasswordAuthenticationFilter之前）
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
