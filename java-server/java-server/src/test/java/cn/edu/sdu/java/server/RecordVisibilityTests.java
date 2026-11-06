package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.GameController;
import cn.edu.sdu.java.server.controllers.AdminRecordController;
import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.RecordVisibility;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.RecordVisibilityRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.AdminRecordService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.RecordVisibilityService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 隐藏测试保留真实记录与会话对象，仓库替身承接所有读写 */
class RecordVisibilityTests {
    /** 测试完成后清理当前账户 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 切换查看账户，普通玩家与管理员分别使用自己的隐藏选择 */
    private static void login(int userId, String role) {
        User user = new User();
        UserType type = new UserType();
        type.setName(role);
        user.setUserId(userId);
        user.setUserName("viewer-" + userId);
        user.setPassword("test");
        user.setUserType(type);
        UserDetailsImpl details = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /** 为一条已通关记录准备原游戏、时长、分数与奖励，隐藏服务独立维护偏好 */
    private static final class Fixture {
        private final GameRecordRepository records = mock(GameRecordRepository.class);
        private final RecordVisibilityRepository visibility = mock(RecordVisibilityRepository.class);
        private final UserRepository users = mock(UserRepository.class);
        private final GamePlayRepository plays = mock(GamePlayRepository.class);
        private final GameRepository games = mock(GameRepository.class);
        private final User owner = new User();
        private final Game game = new Game();
        private final GameRecord record = new GameRecord();
        private final GamePlay play = new GamePlay();
        private final RecordVisibilityService service =
            new RecordVisibilityService(records, visibility, users);
        private final GameService gameService = new GameService(games, users, records, plays, service);

        /** 仓库只返回内存对象，初始记录由玩家二号持有 */
        Fixture() {
            owner.setUserId(2);
            owner.setUserName("player");
            game.setGameId(8);
            game.setName("示例游戏");
            owner.getGames().add(game);
            owner.getFavorites().add(game);
            record.setRecordId(4);
            record.setUser(owner);
            record.setGame(game);
            record.setScore(100);
            record.setDurationSeconds(3600);
            record.setPlayedAt(LocalDateTime.of(2026, 11, 6, 12, 0));
            play.setOutcome("passed");
            play.setAwardedAmount(15L);
            play.setLevelName("第 1 关");
            when(records.findById(4)).thenReturn(Optional.of(record));
            when(records.findByUserUserIdOrderByPlayedAtDesc(2)).thenReturn(List.of(record));
            when(records.findAllByOrderByPlayedAtDesc()).thenReturn(List.of(record));
            when(plays.findByRecordId(4)).thenReturn(Optional.of(play));
            when(users.lockAccount(anyInt())).thenReturn(Optional.of(owner));
            login(2, "ROLE_GAME_USER");
        }

        /** 固定记录编号便于比较原始数据和隐藏结果 */
        DataRequest request() {
            DataRequest request = new DataRequest();
            request.add("recordId", 4);
            return request;
        }
    }

    /** 隐藏后原接口仍返回完整统计资料，重复隐藏复用原偏好且奖励保持原值 */
    @Test
    void hidePreservesRecordSessionAndStatistics() {
        Fixture fixture = new Fixture();
        assertEquals(0, fixture.gameService.hideRecord(fixture.request()).getCode());
        ArgumentCaptor<RecordVisibility> saved = ArgumentCaptor.forClass(RecordVisibility.class);
        verify(fixture.visibility).save(saved.capture());
        RecordVisibility hidden = saved.getValue();
        assertEquals("2:4", hidden.getVisibilityId());
        assertNotNull(hidden.getHiddenAt());
        when(fixture.visibility.existsById("2:4")).thenReturn(true);
        assertEquals(0, fixture.service.hide(fixture.request()).getCode());
        verify(fixture.visibility, times(1)).save(any());
        when(fixture.visibility.findByUserIdAndRecordIdIn(2, List.of(4))).thenReturn(List.of(hidden));
        List<?> rows = (List<?>) fixture.gameService.records().getData();
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(true, row.get("hidden"));
        assertEquals(3600, row.get("durationSeconds"));
        assertEquals(100, row.get("score"));
        assertEquals(15L, row.get("reward"));
        assertEquals("passed", fixture.play.getOutcome());
        assertEquals(List.of(fixture.game), fixture.owner.getGames());
        assertEquals(List.of(fixture.game), fixture.owner.getFavorites());
        verify(fixture.records, never()).delete(any());
        verify(fixture.records, never()).deleteById(anyInt());
        verify(fixture.records, never()).save(any());
        verify(fixture.plays, never()).save(any());
        verify(fixture.users, never()).save(any());

        login(99, "ROLE_ADMIN");
        rows = (List<?>) fixture.gameService.records().getData();
        row = (Map<?, ?>) rows.get(0);
        assertEquals(false, row.get("hidden"));
        assertEquals(0, fixture.service.hide(fixture.request()).getCode());
        verify(fixture.visibility).existsById("99:4");
    }

    /** 普通用户跨账户访问、缺失记录和非法编号均在保存偏好前返回 */
    @Test
    void invalidOrForeignRecordKeepsVisibility() {
        Fixture fixture = new Fixture();
        DataRequest request = fixture.request();
        for (String value : List.of("0", "-1", "4.5", "abc", "2147483648")) {
            request.add("recordId", value);
            assertEquals(1, fixture.service.hide(request).getCode());
        }
        assertEquals(1, fixture.service.hide(new DataRequest()).getCode());
        request.add("recordId", 5);
        assertEquals(1, fixture.service.hide(request).getCode());
        login(3, "ROLE_GAME_USER");
        assertEquals(1, fixture.service.hide(fixture.request()).getCode());
        assertEquals(1, fixture.service.restore(fixture.request()).getCode());
        verify(fixture.visibility, never()).save(any());
        verify(fixture.visibility, never()).deleteById(anyString());
        verify(fixture.records, never()).delete(any());
    }

    /** 恢复只移除当前账户的隐藏标记，重复恢复保持原记录与奖励 */
    @Test
    void restoreKeepsRecordAndOtherAccounts() {
        Fixture fixture = new Fixture();
        when(fixture.visibility.existsById("2:4")).thenReturn(true);
        assertEquals(0, fixture.gameService.restoreRecord(fixture.request()).getCode());
        verify(fixture.visibility).deleteById("2:4");
        when(fixture.visibility.existsById("2:4")).thenReturn(false);
        assertEquals(0, fixture.service.restore(fixture.request()).getCode());
        verify(fixture.visibility, times(1)).deleteById(anyString());
        login(99, "ROLE_ADMIN");
        when(fixture.visibility.existsById("99:4")).thenReturn(true);
        assertEquals(0, fixture.service.restore(fixture.request()).getCode());
        verify(fixture.visibility).deleteById("99:4");
        assertEquals(3600, fixture.record.getDurationSeconds());
        assertEquals(15L, fixture.play.getAwardedAmount());
        verify(fixture.records, never()).save(any());
        verify(fixture.records, never()).delete(any());
        verify(fixture.plays, never()).save(any());
    }

    /** 新隐藏地址和旧删除地址均转给隐藏服务，旧浏览器继续保留原记录 */
    @Test
    void previousDeleteRoutesUseHideService() throws Exception {
        GameService games = mock(GameService.class);
        when(games.hideRecord(any())).thenReturn(new DataResponse(0, null, "记录已隐藏"));
        MockMvc client = MockMvcBuilders.standaloneSetup(
            new GameController(games, mock(BlockcoinService.class))).build();
        for (String path : List.of("/api/game/recordHide", "/api/game/recordDelete")) {
            MockHttpServletRequestBuilder request = post(path);
            request.contentType("application/json");
            request.content("{\"data\":{\"recordId\":4}}");
            ResultActions response = client.perform(request);
            response.andExpect(status().isOk());
        }
        verify(games, times(2)).hideRecord(any());
        when(games.restoreRecord(any())).thenReturn(new DataResponse(0, null, null));
        MockHttpServletRequestBuilder restore = post("/api/game/recordRestore");
        restore.contentType("application/json");
        restore.content("{\"data\":{\"recordId\":4}}");
        client.perform(restore).andExpect(status().isOk());
        verify(games).restoreRecord(any());
        AdminRecordService records = mock(AdminRecordService.class);
        when(records.hide(any())).thenReturn(new DataResponse(0, null, "记录已隐藏"));
        client = MockMvcBuilders.standaloneSetup(new AdminRecordController(records)).build();
        for (String path : List.of("/api/admin/records/hide", "/api/admin/records/delete")) {
            MockHttpServletRequestBuilder request = post(path);
            request.contentType("application/json");
            request.content("{\"data\":{\"recordId\":4}}");
            ResultActions response = client.perform(request);
            response.andExpect(status().isOk());
        }
        verify(records, times(2)).hide(any());
        when(records.restore(any())).thenReturn(new DataResponse(0, null, null));
        restore = post("/api/admin/records/restore");
        restore.contentType("application/json");
        restore.content("{\"data\":{\"recordId\":4}}");
        client.perform(restore).andExpect(status().isOk());
        verify(records).restore(any());
    }
}
