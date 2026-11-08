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

    /** 测试结束后清除身份，每项测试分别设置账户主体 */
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    /** 核对页面转发和未知资源缺失状态，两种请求分别断言 */
    @Test
    void frontendRoutesForwardWithoutSwallowingMissingResources() throws Exception {
        MockMvc client = MockMvcBuilders.standaloneSetup(new PageController()).build();
        for (String path : List.of("/MainPage", "/AccountManage", "/BlockcoinManage", "/RecordManage", "/AiSupport")) {
            ResultActions response = client.perform(get(path));
            response.andExpect(status().isOk());
            response.andExpect(forwardedUrl("/index.html"));
        }
        ResultActions missing = client.perform(get("/missing-file.js"));
        missing.andExpect(status().isNotFound());
    }

    /** 核对本机开关和真实连接来源，伪造转发头时仍执行来源检查 */
    @Test
    void accountSwitchRequiresLocalAddressAndExplicitFlag() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        LocalAccountController disabled = new LocalAccountController(users, auth, false, false);
        assertFalse(disabled.available(request));
        LocalAccountController enabled = new LocalAccountController(users, auth, true, false);
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

    /** 本机地址也可能来自反向代理，存在转发标记时不能列出账户或签发凭证 */
    @Test
    void accountSwitchRejectsProxyHeadersOnLoopback() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        LocalAccountController controller = new LocalAccountController(users, auth, true, false);
        for (String address : List.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1")) {
            MockHttpServletRequest direct = new MockHttpServletRequest();
            direct.setRemoteAddr(address);
            assertTrue(controller.available(direct));
            for (String header : List.of("Forwarded", "X-Forwarded-For", "X-Forwarded-Host",
                    "X-Forwarded-Proto", "X-Real-IP")) {
                MockHttpServletRequest proxy = new MockHttpServletRequest();
                proxy.setRemoteAddr(address);
                proxy.addHeader(header, "127.0.0.1");
                assertFalse(controller.available(proxy));
                DataResponse response = controller.list(proxy);
                Map<?, ?> result = (Map<?, ?>) response.getData();
                assertEquals(false, result.get("enabled"));
                assertEquals(List.of(), result.get("accounts"));
                assertThrows(ResponseStatusException.class, new SwitchThroughProxy(controller, proxy));
            }
        }
        verifyNoInteractions(users, auth);
    }

    /** 保存代理请求，由断言执行真实切号入口，不使用匿名回调 */
    private static class SwitchThroughProxy implements org.junit.jupiter.api.function.Executable {
        private final LocalAccountController controller;
        private final MockHttpServletRequest request;

        /** 保存本次要核验的接口和请求 */
        SwitchThroughProxy(LocalAccountController controller, MockHttpServletRequest request) {
            this.controller = controller;
            this.request = request;
        }

        /** 代理访问必须由接口返回禁止状态 */
        public void execute() {
            controller.switchAccount(new DataRequest(), request);
        }
    }

    /** 右上角切换到目标账户时也更新登录时间和次数 */
    @Test
    void accountSwitchRecordsSuccessfulLogin() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        User player = user(2, "player", "ROLE_GAME_USER");
        when(users.findByUserName("player")).thenReturn(Optional.of(player));
        LocalAccountController controller = new LocalAccountController(users, auth, true, false);
        DataRequest data = new DataRequest();
        data.add("username", "player");

        DataResponse response = controller.switchAccount(data, request);

        assertEquals(0, response.getCode());
        verify(auth).recordSuccessfulLogin(player);
        verify(auth).createSession(player);
    }

    /** 演示模式对远端来源直接开放免密切号，供课程在线检查使用 */
    @Test
    void demoModeAllowsRemoteSwitchWithoutLocalFlag() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.30");
        User admin = user(1, "admin", "ROLE_ADMIN");
        when(users.findByUserName("admin")).thenReturn(Optional.of(admin));
        LocalAccountController demo = new LocalAccountController(users, auth, false, true);
        assertTrue(demo.available(request));
        DataRequest data = new DataRequest();
        data.add("username", "admin");

        DataResponse response = demo.switchAccount(data, request);

        assertEquals(0, response.getCode());
        verify(auth).recordSuccessfulLogin(admin);
        verify(auth).createSession(admin);
    }

    /** 演示模式关闭时保持原有语义：非本机直连即使打开本机开关也拒绝 */
    @Test
    void withoutDemoModeRemoteSwitchStaysRejected() {
        UserRepository users = mock(UserRepository.class);
        AuthService auth = mock(AuthService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.30");
        LocalAccountController controller = new LocalAccountController(users, auth, true, false);
        assertFalse(controller.available(request));
        try {
            controller.switchAccount(new DataRequest(), request);
            fail("演示关闭时远端请求不应签发登录凭证");
        } catch (ResponseStatusException expected) {
            assertEquals(403, expected.getStatusCode().value());
        }
        verifyNoInteractions(users, auth);
    }

    /** 统一登录入口按数据库角色生成会话，普通账户保持游戏用户身份 */
    @Test
    void unifiedLoginKeepsDatabaseRole() {
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
        org.springframework.http.ResponseEntity<?> response = service.authenticateUser(request);
        assertEquals(200, response.getStatusCode().value());
        cn.edu.sdu.java.server.payload.response.JwtResponse session =
                (cn.edu.sdu.java.server.payload.response.JwtResponse) response.getBody();
        assertNotNull(session);
        assertEquals("ROLE_GAME_USER", session.getRole());
        assertNotNull(ordinary.getLastLoginTime());
        assertEquals(1, ordinary.getLoginCount());
        verify(users).save(ordinary);
        verify(jwt).generateToken(any());
    }

    /** 核对系统账号保留规则和其他账号的角色更新结果 */
    @Test
    void protectedAdminCannotBeRemovedOrDemoted() {
        UserRepository users = mock(UserRepository.class);
        UserTypeRepository roles = mock(UserTypeRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        GameUserService service = new GameUserService(users, roles, records,
            mock(PasswordEncoder.class), mock(BlockcoinWalletRepository.class), mock(MessageService.class));
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
        MessageService messages = mock(MessageService.class);
        service = new GameUserService(users, roles, records,
            mock(PasswordEncoder.class), mock(BlockcoinWalletRepository.class), messages);
        request.add("userId", 2);
        request.add("role", "ROLE_ADMIN");
        assertEquals(0, service.saveAccount(request).getCode());
        assertEquals("ROLE_ADMIN", player.getUserType().getName());
        verify(messages).notifyAccount(anyString(), eq(2), eq("账户资料变更"), contains("账户身份"));
    }

    /** 账户列表展示资金与活动信息，读取尚无钱包的账户时保持原余额状态 */
    @Test
    void accountListShowsImportantInformationWithoutCreatingWallets() {
        UserRepository users = mock(UserRepository.class);
        UserTypeRepository roles = mock(UserTypeRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        BlockcoinWalletRepository wallets = mock(BlockcoinWalletRepository.class);
        GameUserService service = new GameUserService(users, roles, records,
            mock(PasswordEncoder.class), wallets, mock(MessageService.class));
        User player = user(2, "player", "ROLE_GAME_USER");
        player.setCreateTime("2026-11-01 10:00:00");
        player.setLastLoginTime("2026-11-06 10:00:00");
        player.setLoginCount(5);
        Game game = new Game();
        game.setGameId(8);
        player.getGames().add(game);
        player.getFavorites().add(game);
        when(users.findAll()).thenReturn(List.of(player));
        when(wallets.findAll()).thenReturn(List.of());
        when(records.countByUserUserId(2)).thenReturn(3L);
        DataResponse response = service.getGameUserList(new DataRequest());
        List<?> list = (List<?>) response.getData();
        Map<?, ?> account = (Map<?, ?>) list.get(0);
        assertNull(account.get("balance"));
        assertEquals(1, account.get("gameCount"));
        assertEquals(1, account.get("favoriteCount"));
        assertEquals(3L, account.get("recordCount"));
        assertEquals(5, account.get("loginCount"));
        assertEquals(player.getLastLoginTime(), account.get("lastLoginTime"));
        assertFalse(account.containsKey("password"));
        verify(wallets, never()).save(any());
    }

    /** 分数管理遇到小数时返回输入错误，仓库保持原数据 */
    @Test
    void recordsRejectFractionalScores() {
        GameRecordRepository records = mock(GameRecordRepository.class);
        AdminRecordService service = new AdminRecordService(records, mock(GameRepository.class),
                mock(UserRepository.class), mock(GameService.class), mock(GamePlayRepository.class));
        DataRequest request = new DataRequest();
        request.add("userId", 1);
        request.add("gameId", 1);
        request.add("score", 1.5);
        request.add("durationSeconds", 10);
        assertEquals(1, service.save(request).getCode());
        verifyNoInteractions(records);
    }

    /** 新增和修改复用同一记录编号，隐藏通过公共可见性服务处理 */
    @Test
    void administratorsCanCreateUpdateAndHideRecords() {
        GameRecordRepository records = mock(GameRecordRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRepository games = mock(GameRepository.class);
        GameService gameService = mock(GameService.class);
        AdminRecordService service = new AdminRecordService(records, games, users,
            gameService, mock(GamePlayRepository.class));
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
        when(gameService.hideRecord(request)).thenReturn(new DataResponse(0, null, "记录已隐藏"));
        assertEquals(0, service.hide(request).getCode());
        verify(gameService).hideRecord(request);
        verify(records, never()).deleteById(anyInt());
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
        /** AI 服务使用替身，权限检查到达业务层后仅记录调用 */
        @Bean
        AiService aiService() {
            return mock(AiService.class);
        }

        /** 使用真实 AI 控制器上的角色要求 */
        @Bean
        AiController aiController(AiService service) {
            return new AiController(service);
        }
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

    /** AI 控制器仅接受本站角色，其他已认证角色在调用服务前被拒绝 */
    @Test
    void aiRequiresRecognizedAccountRole() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MethodSecurityTest.class)) {
            AiController controller = context.getBean(AiController.class);
            AiService service = context.getBean(AiService.class);
            login(user(7, "unknown-role", "ROLE_OTHER"));
            try {
                controller.status();
                fail("其他角色应返回权限拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            verifyNoInteractions(service);
            login(user(2, "player", "ROLE_GAME_USER"));
            controller.status();
            verify(service).status();
        }
    }
}
