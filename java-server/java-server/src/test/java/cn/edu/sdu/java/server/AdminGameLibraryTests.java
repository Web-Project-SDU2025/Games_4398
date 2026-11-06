package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GamePlay;
import cn.edu.sdu.java.server.models.GameRevocation;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.GameRevocationRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.AdminGameLibraryService;
import cn.edu.sdu.java.server.services.BlockcoinService;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.MessageService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 游戏库管理使用仓库替身检查归属、撤回记录和会话状态 */
class AdminGameLibraryTests {
    /** 相同编号模拟网络重试，内容改变后应使用新的编号 */
    private static final String OPERATION = "ce2e3140-c77f-4088-885a-e4c7988f0914";

    /** 每个用例完成后清理认证身份 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 准备两款游戏与独立管理员，保留收藏和资金服务供验证 */
    private static final class Fixture {
        private final UserRepository users = mock(UserRepository.class);
        private final GameRepository games = mock(GameRepository.class);
        private final GameRevocationRepository revocations = mock(GameRevocationRepository.class);
        private final GamePlayRepository plays = mock(GamePlayRepository.class);
        private final BlockcoinService coins = mock(BlockcoinService.class);
        private final GameService gameService = mock(GameService.class);
        private final MessageService messages = mock(MessageService.class);
        private final User actor = new User();
        private final User player = new User();
        private final Game selected = new Game();
        private final Game other = new Game();
        private final GamePlay play = new GamePlay();
        private final AdminGameLibraryService service =
            new AdminGameLibraryService(users, games, revocations, plays, coins, gameService, messages);

        /** 各仓库只返回本用例内对象，角色使用真实认证主体 */
        Fixture() {
            UserType type = new UserType();
            type.setName("ROLE_ADMIN");
            actor.setUserId(99);
            actor.setUserName("manager");
            actor.setPassword("test");
            actor.setUserType(type);
            player.setUserId(12);
            selected.setGameId(8);
            other.setGameId(9);
            player.getGames().add(selected);
            player.getGames().add(other);
            player.getFavorites().add(selected);
            when(users.findById(99)).thenReturn(Optional.of(actor));
            when(users.findById(12)).thenReturn(Optional.of(player));
            when(users.lockAccount(12)).thenReturn(Optional.of(player));
            when(games.findById(8)).thenReturn(Optional.of(selected));
            when(games.findAll()).thenReturn(List.of(selected, other));
            when(coins.owns(player, selected)).thenReturn(true);
            when(gameService.gameInfo(selected)).thenReturn(Map.of("gameId", 8));
            when(plays.findByUserIdAndGameIdAndEndedAtIsNull(12, 8)).thenReturn(List.of(play));
            UserDetailsImpl details = UserDetailsImpl.build(actor);
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        /** 生成完整撤回参数，默认原因供重复请求比较 */
        DataRequest request() {
            DataRequest request = new DataRequest();
            request.add("userId", 12);
            request.add("gameId", 8);
            request.add("reason", "管理调整");
            request.add("requestId", OPERATION);
            return request;
        }
    }

    /** 查看只展示实际持有游戏，撤回只移除选中关联并结束正在运行的会话 */
    @Test
    void revokePreservesOtherGamesFavoritesAndMoney() {
        Fixture fixture = new Fixture();
        DataResponse response = fixture.service.list(fixture.request());
        List<?> library = (List<?>) response.getData();
        assertEquals(1, library.size());
        assertEquals(8, ((Map<?, ?>) library.get(0)).get("gameId"));
        assertEquals(0, fixture.service.revoke(fixture.request()).getCode());
        assertEquals(List.of(fixture.other), fixture.player.getGames());
        assertEquals(List.of(fixture.selected), fixture.player.getFavorites());
        assertEquals("revoked", fixture.play.getOutcome());
        assertNotNull(fixture.play.getEndedAt());
        ArgumentCaptor<GameRevocation> saved = ArgumentCaptor.forClass(GameRevocation.class);
        verify(fixture.revocations).save(saved.capture());
        GameRevocation entry = saved.getValue();
        assertEquals(99, entry.getOperatorId());
        assertEquals("管理调整", entry.getReason());
        assertNotNull(entry.getCreatedAt());
        when(fixture.revocations.findById(entry.getRevocationId())).thenReturn(Optional.of(entry));
        when(fixture.coins.owns(fixture.player, fixture.selected)).thenReturn(true);
        assertEquals(0, fixture.service.revoke(fixture.request()).getCode());
        DataRequest changed = fixture.request();
        changed.add("reason", "另一个原因");
        assertEquals(1, fixture.service.revoke(changed).getCode());
        verify(fixture.users, times(1)).save(fixture.player);
        verify(fixture.revocations, times(1)).save(any());
        verify(fixture.plays, times(1)).save(fixture.play);
        verify(fixture.messages, times(1)).notifyAccount(
            eq("revoke:99:" + OPERATION), eq(12), eq("游戏库变动"), anyString());
        verify(fixture.messages).notifyAccount(
            eq("revoke:99:" + OPERATION), eq(12), eq("游戏库变动"), contains("管理调整"));
        verify(fixture.coins, times(2)).owns(fixture.player, fixture.selected);
        verify(fixture.coins).owns(fixture.player, fixture.other);
        verifyNoMoreInteractions(fixture.coins);
    }

    /** 错误编号、空原因、未持有游戏与失效管理员均保留原游戏库 */
    @Test
    void invalidRevocationKeepsMembership() {
        Fixture fixture = new Fixture();
        DataRequest request = fixture.request();
        request.add("gameId", 1.5);
        assertEquals(1, fixture.service.revoke(request).getCode());
        request = fixture.request();
        request.add("requestId", "invalid");
        assertEquals(1, fixture.service.revoke(request).getCode());
        request = fixture.request();
        request.add("reason", " ");
        assertEquals(1, fixture.service.revoke(request).getCode());
        when(fixture.coins.owns(fixture.player, fixture.selected)).thenReturn(false);
        assertEquals(1, fixture.service.revoke(fixture.request()).getCode());
        fixture.actor.getUserType().setName("ROLE_GAME_USER");
        assertEquals(1, fixture.service.revoke(fixture.request()).getCode());
        assertEquals(2, fixture.player.getGames().size());
        verify(fixture.revocations, never()).save(any());
        verify(fixture.users, never()).save(any());
        verify(fixture.plays, never()).save(any());
    }
}
