package cn.edu.sdu.java.server.configs;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** 使用现有数据源创建增量记账表，并登记首个商品，原连接配置保持原样 */
@Component
public class BlockcoinSetup implements ApplicationRunner {
    private final DataSource source;
    private final GameRepository games;

    /** 参数 source 是原数据源，games 是现有游戏仓库 */
    public BlockcoinSetup(DataSource source, GameRepository games) {
        this.source = source;
        this.games = games;
    }

    /** 启动时补齐新增表，已有商品按入口匹配，重启时保留原资料和余额 */
    @Override
    public void run(ApplicationArguments arguments) {
        ResourceDatabasePopulator script = new ResourceDatabasePopulator(
            new ClassPathResource("blockcoin-schema.sql"));
        script.execute(source);
        for (Game game : games.findAll()) {
            if (BlockcoinRules.PVZ_PATH.equals(game.getGamePath())) {
                return;
            }
        }
        Game game = new Game();
        game.setNum("PVZ");
        game.setName("植物大战僵尸");
        game.setCategory("塔防");
        game.setDescription("守住你的草坪，体验六个关卡、植物图鉴与关卡编辑器");
        game.setGamePath(BlockcoinRules.PVZ_PATH);
        games.save(game);
    }
}
