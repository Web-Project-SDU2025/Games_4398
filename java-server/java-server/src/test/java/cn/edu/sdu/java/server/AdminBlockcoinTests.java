package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.AdminBlockcoinController;
import cn.edu.sdu.java.server.controllers.AdminGameLibraryController;
import cn.edu.sdu.java.server.models.BlockcoinEntry;
import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.BlockcoinEntryRepository;
import cn.edu.sdu.java.server.repositorys.BlockcoinWalletRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.AdminBlockcoinService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.AdminGameLibraryService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 收支查询使用仓库替身验证筛选、分页和身份边界，测试期间保留真实账本 */
class AdminBlockcoinTests {
    /** 测试结束后清除当前身份 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 收支筛选以北京时间包含结束日，分页与累计金额分别返回 */
    @Test
    void filtersPreserveDateBoundariesAndLedgerValues() {
        BlockcoinEntryRepository entries = mock(BlockcoinEntryRepository.class);
        BlockcoinWalletRepository wallets = mock(BlockcoinWalletRepository.class);
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setUserId(12);
        user.setUserName("player");
        user.setName("玩家");
        when(users.existsById(12)).thenReturn(true);
        when(users.findAllById(any())).thenReturn(List.of(user));
        BlockcoinWallet wallet = new BlockcoinWallet();
        wallet.setUserId(12);
        wallet.setBalance(15L);
        when(wallets.findById(12)).thenReturn(Optional.of(wallet));
        BlockcoinEntry entry = new BlockcoinEntry();
        entry.setEntryId("reward:12:one");
        entry.setUserId(12);
        entry.setAmount(15L);
        entry.setBalanceAfter(15L);
        entry.setReason("通关奖励");
        entry.setCreatedAt(Instant.parse("2026-11-06T04:00:00Z"));
        when(entries.searchEntries(any(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(new PageImpl<BlockcoinEntry>(List.of(entry), PageRequest.of(1, 20), 21));
        when(entries.totalIncome(12)).thenReturn(115L);
        when(entries.totalExpense(12)).thenReturn(100L);
        AdminBlockcoinService service = new AdminBlockcoinService(entries, wallets, users);
        DataRequest request = new DataRequest();
        request.add("userId", 12);
        request.add("direction", "income");
        request.add("search", "奖励");
        request.add("startDate", "2026-11-06");
        request.add("endDate", "2026-11-06");
        request.add("page", 2);
        DataResponse response = service.list(request);
        assertEquals(0, response.getCode());
        Map<?, ?> result = (Map<?, ?>) response.getData();
        assertEquals(21L, result.get("total"));
        assertEquals(115L, result.get("income"));
        assertEquals(100L, result.get("expense"));
        assertEquals(15L, result.get("balance"));
        List<?> rows = (List<?>) result.get("entries");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("player", row.get("username"));
        ArgumentCaptor<Pageable> paging = ArgumentCaptor.forClass(Pageable.class);
        verify(entries).searchEntries(eq(12), eq("income"), eq("奖励"),
            eq(Instant.parse("2026-11-05T16:00:00Z")),
            eq(Instant.parse("2026-11-06T16:00:00Z")), paging.capture());
        assertEquals(1, paging.getValue().getPageNumber());
        verify(entries, never()).save(any());
        verify(wallets, never()).save(any());
        verify(users, never()).save(any());
    }

    /** 错误页码、小数、异常日期和方向在查询流水之前返回 */
    @Test
    void invalidQueriesLeaveRepositoriesUntouched() {
        BlockcoinEntryRepository entries = mock(BlockcoinEntryRepository.class);
        BlockcoinWalletRepository wallets = mock(BlockcoinWalletRepository.class);
        UserRepository users = mock(UserRepository.class);
        AdminBlockcoinService service = new AdminBlockcoinService(entries, wallets, users);
        DataRequest request = new DataRequest();
        request.add("pageSize", 101);
        assertEquals(1, service.list(request).getCode());
        request.add("pageSize", 20);
        request.add("page", 1.5);
        assertEquals(1, service.list(request).getCode());
        request.add("page", 1);
        request.add("direction", "other");
        assertEquals(1, service.list(request).getCode());
        request.add("direction", "all");
        request.add("startDate", "2026-02-30");
        assertEquals(1, service.list(request).getCode());
        request.add("startDate", "2026-11-07");
        request.add("endDate", "2026-11-06");
        assertEquals(1, service.list(request).getCode());
        verifyNoInteractions(entries, wallets, users);
    }

    /** 全部账户汇总包含已删除账户的历史流水，缺少身份时保留原账户编号 */
    @Test
    void deletedAccountHistoryRemainsVisible() {
        BlockcoinEntryRepository entries = mock(BlockcoinEntryRepository.class);
        BlockcoinWalletRepository wallets = mock(BlockcoinWalletRepository.class);
        UserRepository users = mock(UserRepository.class);
        BlockcoinEntry entry = new BlockcoinEntry();
        entry.setEntryId("initial:44");
        entry.setUserId(44);
        entry.setAmount(100L);
        when(entries.searchEntries(any(), anyString(), anyString(), any(), any(), any()))
            .thenReturn(new PageImpl<BlockcoinEntry>(List.of(entry)));
        when(users.findAllById(any())).thenReturn(List.of());
        when(wallets.totalBalance()).thenReturn(100L);
        AdminBlockcoinService service = new AdminBlockcoinService(entries, wallets, users);
        DataResponse response = service.list(new DataRequest());
        Map<?, ?> result = (Map<?, ?>) response.getData();
        List<?> rows = (List<?>) result.get("entries");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(44, row.get("userId"));
        assertEquals("", row.get("username"));
        assertEquals(100L, result.get("balance"));
    }

    /** 普通用户实际调用控制器时由方法权限拒绝，管理员才能进入查询服务 */
    @Test
    void ledgerQueryRequiresAdministrator() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SecurityFixture.class)) {
            AdminBlockcoinController controller = context.getBean(AdminBlockcoinController.class);
            UserType type = new UserType();
            type.setName("ROLE_GAME_USER");
            User user = new User();
            user.setUserId(12);
            user.setUserName("player");
            user.setPassword("test");
            user.setUserType(type);
            UserDetailsImpl details = UserDetailsImpl.build(user);
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            try {
                controller.list(new DataRequest());
                fail("普通用户应被管理接口拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            try {
                controller.adjust(new DataRequest());
                fail("普通用户应被资金调整接口拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            AdminGameLibraryController library = context.getBean(AdminGameLibraryController.class);
            try {
                library.list(new DataRequest());
                fail("普通用户应被账户游戏库接口拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            try {
                library.revoke(new DataRequest());
                fail("普通用户应被撤回接口拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            AdminBlockcoinService service = context.getBean(AdminBlockcoinService.class);
            BlockcoinService coins = context.getBean(BlockcoinService.class);
            AdminGameLibraryService libraries = context.getBean(AdminGameLibraryService.class);
            verifyNoInteractions(service, coins, libraries);
            type.setName("ROLE_ADMIN");
            details = UserDetailsImpl.build(user);
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            controller.list(new DataRequest());
            verify(service).list(any());
            controller.adjust(new DataRequest());
            library.list(new DataRequest());
            library.revoke(new DataRequest());
            verify(coins).adjust(any());
            verify(libraries).list(any());
            verify(libraries).revoke(any());
        }
    }

    /** 权限测试只加载真实控制器与业务替身 */
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class SecurityFixture {
        /** 提供收支服务替身，记录权限校验之后的调用 */
        @Bean
        AdminBlockcoinService service() {
            return mock(AdminBlockcoinService.class);
        }

        /** 提供资金操作替身，权限检查直接使用真实控制器 */
        @Bean
        BlockcoinService coins() {
            return mock(BlockcoinService.class);
        }

        /** 使用游戏库替身承接真实控制器通过的请求 */
        @Bean
        AdminGameLibraryService libraries() {
            return mock(AdminGameLibraryService.class);
        }

        /** 两个游戏库入口共用控制器声明的管理员权限 */
        @Bean
        AdminGameLibraryController libraryController(AdminGameLibraryService service) {
            return new AdminGameLibraryController(service);
        }

        /** 保留真实控制器上的管理员权限声明 */
        @Bean
        AdminBlockcoinController controller(AdminBlockcoinService service, BlockcoinService coins) {
            return new AdminBlockcoinController(service, coins);
        }
    }
}
