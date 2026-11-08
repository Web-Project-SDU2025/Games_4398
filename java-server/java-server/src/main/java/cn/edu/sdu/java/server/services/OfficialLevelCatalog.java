package cn.edu.sdu.java.server.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import cn.edu.sdu.java.server.util.BlockcoinRules;
import cn.edu.sdu.java.server.util.GameplayRules;
import cn.edu.sdu.java.server.models.Game;

/**
 * 官方关卡验证服务
 *
 * 从服务器配置文件读取各游戏的奖励规则，并验证关卡文件的完整性
 * 只有官方关卡文件哈希匹配的才能发放奖励，防止玩家自己改关卡刷币
 */
@Service
public class OfficialLevelCatalog {
    /** 路径只能包含字母数字和常见符号，防止目录穿越攻击 */
    private static final String RELATIVE_PATH = "[a-zA-Z0-9_./-]+";
    private final ObjectMapper mapper;

    /**
     * 依赖注入
     * 复用Spring的JSON解析器，所有规则从服务器发布文件读取
     */
    public OfficialLevelCatalog(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 读取游戏配置文件
     * 只有在registry.json里登记过的游戏才能读配置，未登记的返回空配置
     */
    private JsonNode manifest(String path) throws Exception {
        if (path == null || !path.matches(GameplayRules.GAME_PATH_PATTERN)) {
            throw new IllegalArgumentException("游戏入口不正确");
        }
        try (InputStream input = new ClassPathResource("public/games/registry.json").getInputStream()) {
            boolean registered = false;
            for (JsonNode entry : mapper.readTree(input)) {
                if (path.equals(entry.asText())) {
                    registered = true;
                }
            }
            if (!registered) {
                return mapper.createObjectNode();
            }
        }
        try (InputStream input = new ClassPathResource("public" + path).getInputStream()) {
            return mapper.readTree(input);
        }
    }

    /**
     * 验证相对路径的安全性
     * 路径必须在游戏目录内，不能有空段、单点或双点，防止穿越到其他目录
     */
    private String relative(String value) {
        if (!value.matches(RELATIVE_PATH) || value.startsWith("/")) {
            throw new IllegalArgumentException("游戏规则路径不正确");
        }
        for (String part : value.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("游戏规则路径不正确");
            }
        }
        return value;
    }

    /**
     * 计算关卡最短通关时间
     * 从关卡文件里解析最后一个事件的时间，除以最大倍速得到最短通关秒数
     * 比如植物大战僵尸的最后一波僵尸在500秒出现，就算玩家用5倍速也得100秒才能通关
     * 这样可以防止玩家改时间戳作弊
     */
    private int minimumSeconds(byte[] bytes, JsonNode policy) throws Exception {
        long lastTime = 0;
        String arrayName = policy.path("timeArray").asText("");
        String field = policy.path("timeField").asText("");
        if (!arrayName.isEmpty() && !field.isEmpty()) {
            JsonNode level = mapper.readTree(bytes);
            for (JsonNode item : level.path(arrayName)) {
                lastTime = Math.max(lastTime, item.path(field).asLong());
            }
        }
        return (int) Math.max(BlockcoinRules.MIN_PLAY_SECONDS,
            lastTime / 1000 / BlockcoinRules.MAX_SPEED);
    }

    /**
     * 查找关卡验证规则
     * 返回关卡文件的哈希值、最短通关时间和奖励金额
     * 只有文件哈希匹配的关卡才能发奖励，这样玩家改关卡也没用
     * 未配置奖励的游戏返回null，仍可记录游玩时长
     */
    public LevelRule find(String gamePath, Integer number) {
        if (number == null || number < 0) {
            return null;
        }
        try {
            JsonNode source = manifest(gamePath);
            JsonNode policy = source.path("gameplay");
            long reward = policy.path("reward").asLong(0);
            if (reward < 0 || reward > GameplayRules.MAX_LEVEL_REWARD || !policy.has("levelDirectory")) {
                return null;
            }
            String directory = relative(policy.path("levelDirectory").asText());
            String name = directory + "/" + policy.path("levelPrefix").asText("level_") + number + ".json";
            relative(name);
            String root = gamePath.substring(0, gamePath.lastIndexOf("/") + 1);
            String assets = relative(source.path("assets").asText());
            try (InputStream input = new ClassPathResource("public" + root + assets).getInputStream();
                ZipInputStream archive = new ZipInputStream(input)) {
                ZipEntry entry = archive.getNextEntry();
                while (entry != null) {
                    if (name.equals(entry.getName())) {
                        byte[] bytes = archive.readAllBytes();
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        String hash = HexFormat.of().formatHex(digest.digest(bytes));
                        return new LevelRule(hash, minimumSeconds(bytes, policy), reward);
                    }
                    entry = archive.getNextEntry();
                }
            }
            return null;
        } catch (Exception error) {
            throw new IllegalStateException("游戏关卡规则读取失败", error);
        }
    }

    /**
     * 读取商城显示的通关奖励
     * 从游戏配置文件读取默认奖励金额，商城页面会显示这个数字
     */
    public long reward(String path) {
        try {
            return Math.max(0, Math.min(GameplayRules.MAX_LEVEL_REWARD,
                manifest(path).path("gameplay").path("reward").asLong(0)));
        } catch (Exception error) {
            return 0;
        }
    }

    /**
     * 读取游戏实际发放的通关奖励
     * 管理员配置的金额优先，null时使用游戏默认配置，显式设0表示不发奖励
     */
    public long reward(Game game) {
        Long configured = game.getLevelReward();
        if (configured == null) {
            return reward(game.getGamePath());
        }
        return Math.max(0, Math.min(GameplayRules.MAX_LEVEL_REWARD, configured));
    }

    /**
     * 关卡验证规则数据类
     * 保存一关的文件哈希、最短通关时间和奖励金额
     */
    public static final class LevelRule {
        public final String hash;
        public final int minimumSeconds;
        public final long reward;

        /**
         * 构造关卡规则
         * hash是关卡文件的SHA-256摘要，seconds是正常通关的最短时间
         */
        public LevelRule(String hash, int seconds, long reward) {
            this.hash = hash;
            minimumSeconds = seconds;
            this.reward = reward;
        }
    }
}
