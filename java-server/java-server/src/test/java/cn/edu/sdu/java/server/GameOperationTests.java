package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.GamePlayRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.GameService;
import cn.edu.sdu.java.server.services.RecordVisibilityService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用内存账户与仓库替身验证游戏删除、收藏和持有边界 */
class GameOperationTests {
    /** 游戏库修改请求在访问仓库前返回，手动请求也保持原持有关系 */
    @Test
    void libraryChangesLeaveOwnershipUntouched() {
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        GameService service = new GameService(games, users, records,
            mock(GamePlayRepository.class), mock(RecordVisibilityService.class));
        DataRequest request = new DataRequest();
        request.add("gameId", 12);
        request.add("mode", "library");
        request.add("added", false);
        assertEquals(1, service.changeCollection(request).getCode());
        request.add("added", true);
        assertEquals(1, service.changeCollection(request).getCode());
        verifyNoInteractions(games, users, records);
    }

    /** 收藏、重复收藏和取消收藏仅改变收藏列表，购买后的游戏继续留在游戏库 */
    @Test
    void favoriteChangesKeepOwnedGames() {
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        Game selected = new Game();
        selected.setGameId(12);
        User owner = new User();
        owner.getGames().add(selected);
        GameService service = spy(new GameService(games, users, records,
            mock(GamePlayRepository.class), mock(RecordVisibilityService.class)));
        doReturn(owner).when(service).currentUser();
        when(games.findById(12)).thenReturn(Optional.of(selected));
        DataRequest request = new DataRequest();
        request.add("gameId", 12);
        request.add("mode", "favorites");
        request.add("added", true);
        assertEquals(0, service.changeCollection(request).getCode());
        assertEquals(0, service.changeCollection(request).getCode());
        assertEquals(List.of(selected), owner.getFavorites());
        request.add("added", false);
        assertEquals(0, service.changeCollection(request).getCode());
        assertTrue(owner.getFavorites().isEmpty());
        assertEquals(List.of(selected), owner.getGames());
    }

    /** 管理员确认关联清理后，先清理记录和多对多关联，再删除游戏 */
    @Test
    void confirmedDeletionClearsOnlySelectedGameRelations() {
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        Game selected = new Game();
        selected.setGameId(12);
        Game other = new Game();
        other.setGameId(13);
        User owner = new User();
        owner.getGames().add(selected);
        owner.getGames().add(other);
        owner.getFavorites().add(selected);
        User another = new User();
        another.getFavorites().add(selected);
        User untouched = new User();
        untouched.getGames().add(other);
        when(games.findById(12)).thenReturn(Optional.of(selected));
        when(users.findAll()).thenReturn(List.of(owner, another, untouched));
        DataRequest request = new DataRequest();
        request.add("gameId", 12);
        request.add("removeRelated", true);
        GameService service = new GameService(games, users, records,
            mock(GamePlayRepository.class), mock(RecordVisibilityService.class));
        DataResponse result = service.delete(request);
        assertEquals(0, result.getCode());
        assertEquals(List.of(other), owner.getGames());
        assertTrue(owner.getFavorites().isEmpty());
        assertTrue(another.getFavorites().isEmpty());
        assertEquals(List.of(other), untouched.getGames());
        verify(users, never()).save(untouched);
        InOrder order = inOrder(records, users, games);
        order.verify(records).deleteByGameGameId(12);
        order.verify(users).save(owner);
        order.verify(users).save(another);
        order.verify(users).flush();
        order.verify(records).flush();
        order.verify(games).delete(selected);
    }

    /** 旧请求未带确认标记时继续保护记录，重复删除和缺少编号返回明确提示 */
    @Test
    void deletionRequiresConfirmationAndHandlesMissingGame() {
        GameRepository games = mock(GameRepository.class);
        UserRepository users = mock(UserRepository.class);
        GameRecordRepository records = mock(GameRecordRepository.class);
        Game selected = new Game();
        selected.setGameId(12);
        when(games.findById(12)).thenReturn(Optional.of(selected));
        when(users.findAll()).thenReturn(List.of());
        when(records.existsByGameGameId(12)).thenReturn(true);
        GameService service = new GameService(games, users, records,
            mock(GamePlayRepository.class), mock(RecordVisibilityService.class));
        DataRequest request = new DataRequest();
        request.add("gameId", 12);
        assertEquals(1, service.delete(request).getCode());
        verify(records, never()).deleteByGameGameId(anyInt());
        verify(games, never()).delete(any(Game.class));
        when(games.findById(12)).thenReturn(Optional.empty());
        request.add("removeRelated", true);
        assertTrue(service.delete(request).getMsg().contains("已删除"));
        assertTrue(service.delete(new DataRequest()).getMsg().contains("指定"));
    }
}
