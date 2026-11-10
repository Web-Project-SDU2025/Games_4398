package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.services.InstalledGameCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用仓库替身和真实发布清单检查游戏登记，不连接数据库 */
class InstalledGameCatalogTests {
    /** 旧商品沿用安装金额，管理员零值和自定义金额优先于安装默认值 */
    @Test
    void administratorRewardOverridesManifest() {
        cn.edu.sdu.java.server.services.OfficialLevelCatalog catalog =
                new cn.edu.sdu.java.server.services.OfficialLevelCatalog(new ObjectMapper());
        Game game = new Game();
        game.setGamePath("/games/pvz/game.json");
        assertEquals(15L, catalog.reward(game));
        game.setLevelReward(0L);
        assertEquals(0L, catalog.reward(game));
        game.setLevelReward(27L);
        assertEquals(27L, catalog.reward(game));
    }
    /** 两款游戏已存在时只补小丑牌，已有资料保持管理员设置的原值 */
    @Test
    void registersThirdGameWithoutReplacingExistingProducts() throws Exception {
        GameRepository repository = mock(GameRepository.class);
        Game first = new Game();
        first.setGamePath("/games/pvz/game.json");
        first.setName("管理员维护的名称");
        Game second = new Game();
        second.setGamePath("/games/billiards/game.json");
        second.setName("管理员维护的台球名称");
        when(repository.findAll()).thenReturn(List.of(first, second));
        InstalledGameCatalog catalog = new InstalledGameCatalog(repository, new ObjectMapper());
        catalog.registerMissing();
        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(repository).save(saved.capture());
        assertEquals("/games/joker-table/game.json", saved.getValue().getGamePath());
        assertEquals("JOKER-TABLE", saved.getValue().getNum());
        assertEquals("小丑牌", saved.getValue().getName());
        assertEquals("管理员维护的名称", first.getName());
        assertEquals("管理员维护的台球名称", second.getName());
    }

    /** 默认旧名称在原商品记录上更新，避免影响购买和存档 */
    @Test
    void upgradesExistingDefaultJokerNameInPlace() throws Exception {
        GameRepository repository = mock(GameRepository.class);
        Game first = new Game();
        first.setGamePath("/games/pvz/game.json");
        Game second = new Game();
        second.setGamePath("/games/billiards/game.json");
        Game alias = new Game();
        alias.setGameId(16);
        alias.setNum("OTHER-TABLE");
        alias.setGamePath("/games/joker-table/game.json");
        alias.setName("夜场牌局");
        Game third = new Game();
        third.setGameId(17);
        third.setNum("JOKER-TABLE");
        third.setGamePath("/games/joker-table/game.json");
        third.setName("夜场牌局");
        when(repository.findAll()).thenReturn(List.of(first, second, alias, third));
        InstalledGameCatalog catalog = new InstalledGameCatalog(repository, new ObjectMapper());
        catalog.registerMissing();
        verify(repository).renameIfUnchanged(17, "JOKER-TABLE", "/games/joker-table/game.json", "夜场牌局", "小丑牌");
        verify(repository, never()).save(any(Game.class));
    }

    /** 重复启动保留当前名称和自定义资料，兼容入口相同但编号不同的商品 */
    @ParameterizedTest
    @CsvSource({"JOKER-TABLE, 小丑牌", "JOKER-TABLE, 管理员维护的牌名", "JOKER-TABLE, '夜场牌局 '", "OTHER-TABLE, 夜场牌局"})
    void existingProductsAreNotDuplicatedOrOverwritten(String num, String name) throws Exception {
        GameRepository repository = mock(GameRepository.class);
        Game first = new Game();
        first.setGamePath("/games/pvz/game.json");
        Game second = new Game();
        second.setGamePath("/games/billiards/game.json");
        Game third = new Game();
        third.setGameId(17);
        third.setNum(num);
        third.setGamePath("/games/joker-table/game.json");
        third.setName(name);
        when(repository.findAll()).thenReturn(List.of(first, second, third));
        InstalledGameCatalog catalog = new InstalledGameCatalog(repository, new ObjectMapper());
        catalog.registerMissing();
        verify(repository, never()).save(any(Game.class));
        verify(repository, never()).renameIfUnchanged(any(), any(), any(), any(), any());
    }

    /** 原生游戏没有通用关卡奖励配置，平台仍可按通用方式记录游玩时长 */
    @Test
    void nativeGamesHaveNoOfficialLevelReward() {
        cn.edu.sdu.java.server.services.OfficialLevelCatalog catalog =
                new cn.edu.sdu.java.server.services.OfficialLevelCatalog(new ObjectMapper());
        assertEquals(0, catalog.reward("/games/billiards/game.json"));
        assertNull(catalog.find("/games/billiards/game.json", 0));
        assertEquals(0, catalog.reward("/games/joker-table/game.json"));
        assertNull(catalog.find("/games/joker-table/game.json", 0));
    }
}
