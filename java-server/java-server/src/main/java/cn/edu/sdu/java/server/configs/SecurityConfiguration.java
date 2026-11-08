package cn.edu.sdu.java.server.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置类
 *
 * 统一装配登录与访问规则
 * 每次API请求独立校验身份
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 构造函数
     * 保存认证服务和登录过滤器，供过滤链装配使用
     */
    public SecurityConfiguration(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        AuthenticationProvider authenticationProvider
    ) {
        this.authenticationProvider = authenticationProvider;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 安全过滤链Bean
     * 配置API登录校验和无状态会话，返回网站过滤链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(new DisableCsrf());
        http.cors(new ConfigureCors(corsConfigurationSource()));
        http.headers(new ConfigureGameHeaders());
        http.authorizeHttpRequests(new ConfigureAccess());
        http.sessionManagement(new ConfigureSessions());
        http.authenticationProvider(authenticationProvider);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 保留现有跨域策略，只维护一份配置供过滤链使用 */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",configuration);
        return source;
    }

    /** 为原 Java 游戏启动页增加同源嵌入规则，认证和服务器连接配置沿用现有设置 */
    private static class ConfigureGameHeaders implements Customizer<HeadersConfigurer<HttpSecurity>> {
        /** 将游戏页面规则追加到默认响应头之后，其余页面继续由默认规则保护 */
        @Override
        public void customize(HeadersConfigurer<HttpSecurity> configuration) {
            configuration.addHeaderWriter(new GameFrameHeaders());
        }
    }

    /** JWT 使用请求头传递，关闭基于浏览器会话的 CSRF 校验 */
    private static class DisableCsrf implements Customizer<CsrfConfigurer<HttpSecurity>> {
        /** 接收 Spring 的 CSRF 配置对象并关闭它 */
        @Override
        public void customize(CsrfConfigurer<HttpSecurity> configuration) {
            configuration.disable();
        }
    }

    /** 命名配置类负责连接跨域策略，过滤链复用相同配置 */
    private static class ConfigureCors implements Customizer<CorsConfigurer<HttpSecurity>> {
        private final CorsConfigurationSource source;

        /** 保存要使用的跨域策略 */
        private ConfigureCors(CorsConfigurationSource source) {
            this.source = source;
        }

        /** 将跨域策略交给 Spring Security */
        @Override
        public void customize(CorsConfigurer<HttpSecurity> configuration) {
            configuration.configurationSource(source);
        }
    }

    /** API 要求登录，其他页面沿用公开访问规则 */
    private static class ConfigureAccess implements Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> {
        /** 配置请求地址与访问条件 */
        @Override
        public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry configuration) {
            configuration.requestMatchers("/api/**").authenticated();
            configuration.anyRequest().permitAll();
        }
    }

    /** 每次请求独立校验 JWT，不建立服务器登录会话 */
    private static class ConfigureSessions implements Customizer<SessionManagementConfigurer<HttpSecurity>> {
        /** 设置无状态会话模式 */
        @Override
        public void customize(SessionManagementConfigurer<HttpSecurity> configuration) {
            configuration.sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        }
    }
}
