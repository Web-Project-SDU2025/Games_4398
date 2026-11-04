package cn.edu.sdu.java.server.configs;

import cn.edu.sdu.java.server.services.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;

/** 装配密码与认证组件，登录失败由认证组件按原规则处理 */
@Configuration
public class ApplicationConfiguration {
    private final UserDetailsServiceImpl userDetailsService;

    /** 保存用户查询服务，认证时读取数据库中的当前账户 */
    public ApplicationConfiguration(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /** 提供 BCrypt 密码编码器，注册和登录使用相同算法 */
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 在当前请求中保存认证结果，请求结束后由框架清理 */
    @Bean
    public RequestAttributeSecurityContextRepository getRequestAttributeSecurityContextRepository() {
        return new RequestAttributeSecurityContextRepository();
    }

    /** 取得框架认证管理器，配置异常交给启动过程报告 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /** 连接用户查询和密码校验，账号缺失时由认证服务返回失败 */
    @Bean
    AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }
}

