package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.*;
import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.services.*;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 全部使用内存对象和仓库替身，验证权限和路由，不连接课程数据库 */
class ManagementTests {
    /** 创建具有明确角色的测试账户 */
    private User user(int id, String username, String role) {
        UserType type = new UserType();
        type.setName(role);
        User user = new User();
        user.setUserId(id);
        user.setUserName(username);
        user.setName(username);
        user.setPassword("test-hash");
        user.setUserType(type);
        return user;
    }

    /** 放入与真实 JWT 过滤器相同的账户主体，供服务读取当前身份 */
    private void login(User user) {
        UserDetailsImpl details = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 测试结束后清除身份，避免下一项测试继承管理员权限 */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    /** 前端页面刷新被转发，未知资源不会被误当成首页 */
    @Test
    void frontendRoutesForwardWithoutSwallowingMissingResources() throws Exception {
        MockMvc client = MockMvcBuilders.standaloneSetup(new PageController()).build();
        for (String path : List.of("/MainPage", "/AdminLogin", "/AccountManage", "/AiSupport")) {
            ResultActions response = client.perform(get(path));
            response.andExpect(status().isOk());
            response.andExpect(forwardedUrl("/index.html"));
        }
        ResultActions missing = client.perform(get("/missing-file.js"));
        missing.andExpect(status().isNotFound());
    }

    /** 本地开关关闭或远端请求时，即便伪造转发头也不能免密切号 */
    @Test
    void accountSwitchRequiresLocalAddressAndExplicitFlag() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        LocalAccountController disabled = new LocalAccountController(users, auth, false);
        assertFalse(disabled.available(request));
        LocalAccountController enabled = new LocalAccountController(users, auth, true);
        assertTrue(enabled.available(request));
        request.setRemoteAddr("192.0.2.20");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        assertFalse(enabled.available(request));
        try {
            enabled.switchAccount(new DataRequest(), request);
            fail("远端请求不应签发登录凭证");
        } catch (ResponseStatusException expected) {
            assertEquals(403, expected.getStatusCode().value());
        }
        verifyNoInteractions(users, auth);
    }

    /** 管理员入口不能把密码正确的普通账户提升为管理员 */
    @Test
    void administratorLoginRejectsOrdinaryAccount() {
        UserRepository users = mock(UserRepository.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        GameUserService profiles = mock(GameUserService.class);
        AuthService service = new AuthService(users, manager, jwt, profiles);
        User ordinary = user(2, "player", "ROLE_GAME_USER");
        UserDetailsImpl details = UserDetailsImpl.build(ordinary);
        UsernamePasswordAuthenticationToken accepted = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        when(manager.authenticate(any())).thenReturn(accepted);
        when(users.findById(2)).thenReturn(Optional.of(ordinary));
        Map<String, Object> captcha = LoginControlUtil.getInstance().getValidateCodeDataMap();
        Integer id = (Integer) captcha.get("validateCodeId");
        LoginRequest request = new LoginRequest();
        request.setUsername("player");
        request.setPassword("test");
        request.setValidateCodeId(id);
        request.setValidateCode(LoginControlUtil.getInstance().getValidateCode(id));
        assertEquals(403, service.authenticateAdmin(request).getStatusCode().value());
        verify(users, never()).save(any());
        verifyNoInteractions(jwt);
    }

    /** admin 不能删除或降级，其他账号可以由管理员设置角色 */
    @Test
    void protectedAdminCannotBeRemovedOrDemoted() {
        UserRepository users = mock(UserRepository.class);
        UserTypeRepository roles = mock(UserTypeRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        GameUserService service = new GameUserService(users, roles, records, mock(PasswordEncoder.class));
        login(user(9, "reviewer", "ROLE_ADMIN"));
        User admin = user(1, "admin", "ROLE_ADMIN");
        when(users.findById(1)).thenReturn(Optional.of(admin));
        DataRequest request = new DataRequest();
        request.add("userId", 1);
        assertEquals(1, service.deleteAccount(request).getCode());
        request.add("role", "ROLE_GAME_USER");
        assertEquals(1, service.saveAccount(request).getCode());
        verify(users, never()).delete(any());
        User player = user(2, "player", "ROLE_GAME_USER");
        when(users.findById(2)).thenReturn(Optional.of(player));
        when(roles.findByName("ROLE_ADMIN")).thenReturn(admin.getUserType());
        request.add("userId", 2);
        request.add("role", "ROLE_ADMIN");
        assertEquals(0, service.saveAccount(request).getCode());
        assertEquals("ROLE_ADMIN", player.getUserType().getName());
    }

    /** 分数管理拒绝小数，避免原请求工具把 1.5 静默转换为 1 */
    @Test
    void recordsRejectFractionalScores() {
        GameRecordRepository records = mock(GameRecordRepository.class);
        AdminRecordService service = new AdminRecordService(records, mock(GameRepository.class),
                mock(UserRepository.class), mock(GameService.class));
        DataRequest request = new DataRequest();
        request.add("userId", 1);
        request.add("gameId", 1);
        request.add("score", 1.5);
        request.add("durationSeconds", 10);
        assertEquals(1, service.save(request).getCode());
        verifyNoInteractions(records);
    }

    /** 新增、修改和删除复用同一编号，分数和记录视图不会出现两份数据 */
    @Test
    void administratorsCanCreateUpdateAndDeleteRecords() {
        GameRecordRepository records = mock(GameRecordRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRepository games = mock(GameRepository.class);
        AdminRecordService service = new AdminRecordService(records, games, users, mock(GameService.class));
        User player = user(2, "player", "ROLE_GAME_USER");
        Game game = new Game();
        game.setGameId(1);
        when(users.findById(2)).thenReturn(Optional.of(player));
        when(games.findById(1)).thenReturn(Optional.of(game));
        DataRequest request = new DataRequest();
        request.add("userId", 2);
        request.add("gameId", 1);
        request.add("score", 12);
        request.add("durationSeconds", 60);
        request.add("playedAt", "2026-11-03T10:00:00");
        assertEquals(0, service.save(request).getCode());
        ArgumentCaptor<GameRecord> captured = ArgumentCaptor.forClass(GameRecord.class);
        verify(records).save(captured.capture());
        GameRecord saved = captured.getValue();
        assertEquals(player, saved.getUser());
        assertEquals(12, saved.getScore());
        saved.setRecordId(4);
        when(records.findById(4)).thenReturn(Optional.of(saved));
        request.add("recordId", 4);
        request.add("score", 77);
        assertEquals(0, service.save(request).getCode());
        assertEquals(77, saved.getScore());
        assertEquals(60, saved.getDurationSeconds());
        when(records.existsById(4)).thenReturn(true);
        assertEquals(0, service.delete(request).getCode());
        verify(records).deleteById(4);
    }

    /** 普通用户实际调用管理员控制器时会被方法权限拒绝 */
    @Test
    void ordinaryRoleCannotCallAdministratorEndpoints() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MethodSecurityTest.class)) {
            AdminRecordController controller = context.getBean(AdminRecordController.class);
            login(user(2, "player", "ROLE_GAME_USER"));
            try {
                controller.list(new DataRequest());
                fail("普通用户不能调用管理员查询");
            } catch (AccessDeniedException expected) {
                assertNotNull(expected);
            }
            login(user(1, "admin", "ROLE_ADMIN"));
            controller.list(new DataRequest());
            AdminRecordService service = context.getBean(AdminRecordService.class);
            verify(service, times(1)).list(any());
        }
    }

    /** 为方法权限测试装配最小容器，不加载数据源或真实服务 */
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityTest {
        /** 提供记录服务替身，便于核对未授权请求是否到达业务层 */
        @Bean
        AdminRecordService records() {
            return mock(AdminRecordService.class);
        }

        /** 使用真实控制器上的权限声明 */
        @Bean
        AdminRecordController controller(AdminRecordService service) {
            return new AdminRecordController(service);
        }
    }
}
