package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.configs.JwtAuthenticationFilter;
import cn.edu.sdu.java.server.configs.SecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用真实安全配置验证响应头，身份过滤器和网页内容由替身提供，全程不连接数据库 */
class GameFrameHeaderTests {
    /** 游戏启动页允许同源嵌入，其他页面保留拒绝嵌入，API 仍要求登录 */
    @Test
    void onlyJavaPlayerAllowsSameOriginFrames() throws Exception {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(IsolatedConfiguration.class);
            context.refresh();
            MockMvc client = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            ResultActions player = client.perform(get("/games/java-player.html?game=pvz"));
            player.andExpect(status().isOk());
            player.andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
            player.andExpect(header().string("Content-Security-Policy", "frame-ancestors 'self'"));
            for (String path : java.util.List.of("/Login", "/games/other.html", "/games/java-player.html/extra")) {
                ResultActions response = client.perform(get(path));
                response.andExpect(header().string("X-Frame-Options", "DENY"));
            }
            client.perform(get("/api/check")).andExpect(status().isForbidden());
        }
    }

    /** 最小网页容器只装配现有安全链和固定网页内容 */
    @TestConfiguration
    @EnableWebMvc
    @Import(SecurityConfiguration.class)
    static class IsolatedConfiguration {
        /** 请求身份只保存在当前测试请求中，不建立外部存储连接 */
        @Bean
        RequestAttributeSecurityContextRepository contextRepository() {
            return new RequestAttributeSecurityContextRepository();
        }

        /** 认证提供者不参与此处的公开静态网页请求 */
        @Bean
        AuthenticationProvider authenticationProvider() {
            return mock(AuthenticationProvider.class);
        }

        /** 过滤器只继续请求，避免读取真实令牌和用户资料 */
        @Bean
        JwtAuthenticationFilter authenticationFilter() {
            return new TestAuthenticationFilter();
        }

        /** 固定响应表示可以返回的站内启动页面 */
        @Bean
        TestPages pages() {
            return new TestPages();
        }
    }

    /** 将网页请求交给后续真实安全链，不创建登录身份 */
    static class TestAuthenticationFilter extends JwtAuthenticationFilter {
        /** 此过滤器只调用测试覆盖的方法，因此不装配真实身份服务 */
        TestAuthenticationFilter() {
            super(null, null, null);
        }

        /** 继续过滤链，让真实响应头规则完成写入 */
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            chain.doFilter(request, response);
        }
    }

    /** 返回最小页面文本，测试只检查路径和 HTTP 响应头 */
    @RestController
    @TestComponent
    static class TestPages {
        /** 对指定测试页面返回可识别内容，接口由实际安全规则保护 */
        @GetMapping({"/games/java-player.html", "/Login", "/games/other.html", "/api/check"})
        String page() {
            return "test-page";
        }
    }
}
