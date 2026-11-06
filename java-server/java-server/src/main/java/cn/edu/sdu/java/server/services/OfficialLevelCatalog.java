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

/** 从服务器发布配置读取各游戏的奖励规则，并核对官方关卡文件 */
@Service
public class OfficialLevelCatalog {
    /** 发布资源只使用游戏目录内的相对路径 */
    private static final String RELATIVE_PATH = "[a-zA-Z0-9_./-]+";
    private final ObjectMapper mapper;

    /** 复用后端 JSON 解析器，规则始终从服务器发布文件读取 */
    public OfficialLevelCatalog(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** 读取站内已登记的配置地址，未登记游戏使用空配置 */
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

    /** 相对路径保持在本游戏目录内，空段和上级目录按错误配置处理 */
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

    /** 按配置中的数组和时间字段读取最晚事件，其他游戏可省略这一规则 */
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

    /** 无关卡或奖励配置的游戏返回空规则，仍可记录游玩时长与结果 */
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

    /** 商城奖励取自本地发布配置，未配置奖励的游戏显示零 */
    public long reward(String path) {
        try {
            return Math.max(0, Math.min(GameplayRules.MAX_LEVEL_REWARD,
                manifest(path).path("gameplay").path("reward").asLong(0)));
        } catch (Exception error) {
            return 0;
        }
    }

    /** 保存一关的指纹与最低经过秒数，字段由服务器素材产生 */
    public static final class LevelRule {
        public final String hash;
        public final int minimumSeconds;
        public final long reward;

        /** 参数 hash 是关卡文件摘要，seconds 是正常结算最早可接受的时间 */
        public LevelRule(String hash, int seconds, long reward) {
            this.hash = hash;
            minimumSeconds = seconds;
            this.reward = reward;
        }
    }
}
