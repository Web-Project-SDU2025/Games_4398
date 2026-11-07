package cn.edu.sdu.java.server.configs;

import cn.edu.sdu.java.server.services.InstalledGameCatalog;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** 使用现有数据源创建增量记账表，并根据游戏发布清单补齐商城商品 */
@Component
public class BlockcoinSetup implements ApplicationRunner {
    private final DataSource source;
    private final InstalledGameCatalog catalog;

    /** 参数 source 是原数据源，catalog 读取本地已发布游戏 */
    public BlockcoinSetup(DataSource source, InstalledGameCatalog catalog) {
        this.source = source;
        this.catalog = catalog;
    }

    /** 启动时补齐新增表，已有商品按入口匹配，重启时保留原资料和余额 */
    @Override
    public void run(ApplicationArguments arguments) throws Exception {
        ResourceDatabasePopulator script = new ResourceDatabasePopulator(
            new ClassPathResource("blockcoin-schema.sql"));
        script.execute(source);
        addGameRewardColumn();
        catalog.registerMissing();
    }

    /** 只为旧游戏表补上可空奖励字段，不覆盖管理员资料或旧游戏的默认奖励 */
    private void addGameRewardColumn() throws Exception {
        try (Connection connection = source.getConnection()) {
            boolean exists;
            try (ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(), null, "game", "level_reward")) {
                exists = columns.next();
            }
            if (!exists) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE game ADD COLUMN level_reward BIGINT NULL");
                }
            }
        }
    }
}
