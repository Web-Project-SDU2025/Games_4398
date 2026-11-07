package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** 按本地发布清单补齐商城商品，已存在的游戏按入口匹配并保留管理员维护的资料 */
@Service
public class InstalledGameCatalog {
    /** 注册表与前端构建的发布位置一致，只读取服务器包内的游戏配置 */
    private static final String REGISTRY = "public/games/registry.json";
    /** 游戏入口只接受发布目录中的英文编号，避免配置越过资源目录 */
    private static final String GAME_PATH = "/games/[a-z][a-z0-9-]{0,39}/game\\.json";
    private final GameRepository games;
    private final ObjectMapper mapper;

    /** 参数 games 是现有商品仓库，mapper 是后端共用的 JSON 解析器 */
    public InstalledGameCatalog(GameRepository games, ObjectMapper mapper) {
        this.games = games;
        this.mapper = mapper;
    }

    /** 启动时登记清单中的缺失商品，重复执行不新增相同入口 */
    public void registerMissing() throws Exception {
        List<Game> existing = games.findAll();
        try (InputStream input = new ClassPathResource(REGISTRY).getInputStream()) {
            JsonNode registry = mapper.readTree(input);
            if (!registry.isArray()) {
                throw new IllegalStateException("游戏注册表应为配置地址数组");
            }
            for (JsonNode entry : registry) {
                String path = entry.asText();
                if (!path.matches(GAME_PATH)) {
                    throw new IllegalStateException("本地游戏配置路径不正确");
                }
                if (!contains(existing, path)) {
                    register(path);
                }
            }
        }
    }

    /** 按入口查找已有商品，名称修改后仍可识别同一个游戏 */
    private boolean contains(List<Game> existing, String path) {
        for (Game game : existing) {
            if (path.equals(game.getGamePath())) {
                return true;
            }
        }
        return false;
    }

    /** 读取单款游戏的发布资料并写入现有商品表，编号冲突时报告明确错误 */
    private void register(String path) throws Exception {
        try (InputStream input = new ClassPathResource("public" + path).getInputStream()) {
            JsonNode manifest = mapper.readTree(input);
            String number = manifest.path("number").asText(manifest.path("id").asText());
            String name = manifest.path("name").asText();
            if (number.isBlank() || name.isBlank() || games.existsByNum(number)) {
                throw new IllegalStateException("已安装游戏的编号缺失或与商城已有商品重复：" + number);
            }
            Game game = new Game();
            game.setNum(number);
            game.setName(name);
            game.setCategory(manifest.path("category").asText("小游戏"));
            game.setDescription(manifest.path("description").asText());
            game.setGamePath(path);
            games.save(game);
        }
    }
}
