package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.services.InstalledGameCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用仓库替身和真实发布清单检查第二款游戏登记，不连接数据库 */
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
    /** 植物大战僵尸已存在时只补台球，已有资料保持管理员设置的原值 */
    @Test
    void registersSecondGameWithoutReplacingFirst() throws Exception {
        GameRepository repository = mock(GameRepository.class);
        Game first = new Game();
        first.setGamePath("/games/pvz/game.json");
        first.setName("管理员维护的名称");
        when(repository.findAll()).thenReturn(List.of(first));
        InstalledGameCatalog catalog = new InstalledGameCatalog(repository, new ObjectMapper());
        catalog.registerMissing();
        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(repository).save(saved.capture());
        assertEquals("/games/billiards/game.json", saved.getValue().getGamePath());
        assertEquals("BILLIARDS", saved.getValue().getNum());
        assertEquals("美式八球", saved.getValue().getName());
        assertEquals("管理员维护的名称", first.getName());
    }

    /** 两款商品都已登记时重复启动不写入，避免产生重复商城条目 */
    @Test
    void existingProductsAreNotDuplicated() throws Exception {
        GameRepository repository = mock(GameRepository.class);
        Game first = new Game();
        first.setGamePath("/games/pvz/game.json");
        Game second = new Game();
        second.setGamePath("/games/billiards/game.json");
        when(repository.findAll()).thenReturn(List.of(first, second));
        InstalledGameCatalog catalog = new InstalledGameCatalog(repository, new ObjectMapper());
        catalog.registerMissing();
        verify(repository, never()).save(any(Game.class));
    }

    /** 台球没有关卡奖励配置，平台仍可按通用方式记录游玩时长 */
    @Test
    void billiardsHasNoOfficialLevelReward() {
        cn.edu.sdu.java.server.services.OfficialLevelCatalog catalog =
                new cn.edu.sdu.java.server.services.OfficialLevelCatalog(new ObjectMapper());
        assertEquals(0, catalog.reward("/games/billiards/game.json"));
        assertNull(catalog.find("/games/billiards/game.json", 0));
    }
}
