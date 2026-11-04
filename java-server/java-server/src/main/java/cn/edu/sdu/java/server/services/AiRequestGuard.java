package cn.edu.sdu.java.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

/** 在调用模型前统一检查身份、并发、频率和每日用量 */
@Component
public class AiRequestGuard {
    // 频率窗口的长度，单位为毫秒
    private static final long MINUTE_MILLISECONDS = 60000;
    // 分钟来源表和每日账号表各自保留的身份数量上限
    private static final int TRACKED_IDENTITY_LIMIT = 10000;
    // 每日计数文件的字节上限，读取前检查文件长度
    private static final long QUOTA_FILE_BYTE_LIMIT = 1024 * 1024;
    // 每日用量使用的日期时区
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Path quotaFile;
    private final int concurrentLimit;
    private final int accountMinuteLimit;
    private final int addressMinuteLimit;
    private final int globalMinuteLimit;
    private final int accountDailyLimit;
    private final int globalDailyLimit;
    private final Map<Integer, Integer> activeAccounts = new HashMap<>();
    private int activeRequests;
    private final Map<String, Integer> minuteAccounts = new HashMap<>();
    private final Map<String, Integer> minuteAddresses = new HashMap<>();
    private long minuteStartedAt;
    private int minuteCount;
    private DailyUsage dailyUsage;

    /** 从配置读取保护上限，零或负数配置会阻止服务启动 */
    @Autowired
    public AiRequestGuard(ObjectMapper mapper,
            @Value("${app.ai.quota-file:runtime/ai-usage.json}") String quotaFile,
            @Value("${app.ai.max-concurrent:5}") int concurrentLimit,
            @Value("${app.ai.account-minute-limit:8}") int accountMinuteLimit,
            @Value("${app.ai.ip-minute-limit:20}") int addressMinuteLimit,
            @Value("${app.ai.global-minute-limit:60}") int globalMinuteLimit,
            @Value("${app.ai.account-daily-limit:40}") int accountDailyLimit,
            @Value("${app.ai.global-daily-limit:500}") int globalDailyLimit) {
        this(mapper, Path.of(quotaFile), Clock.systemUTC(), concurrentLimit,
                accountMinuteLimit, addressMinuteLimit, globalMinuteLimit,
                accountDailyLimit, globalDailyLimit);
    }

    /** 测试使用临时文件和固定时钟，隔离各项测试的计数 */
    public AiRequestGuard(ObjectMapper mapper, Path quotaFile, Clock clock,
            int concurrentLimit, int accountMinuteLimit, int addressMinuteLimit,
            int globalMinuteLimit, int accountDailyLimit, int globalDailyLimit) {
        this.mapper = mapper;
        this.quotaFile = quotaFile.toAbsolutePath().normalize();
        this.clock = clock;
        this.concurrentLimit = positiveLimit(concurrentLimit);
        this.accountMinuteLimit = positiveLimit(accountMinuteLimit);
        this.addressMinuteLimit = positiveLimit(addressMinuteLimit);
        this.globalMinuteLimit = positiveLimit(globalMinuteLimit);
        this.accountDailyLimit = positiveLimit(accountDailyLimit);
        this.globalDailyLimit = positiveLimit(globalDailyLimit);
        dailyUsage = loadUsage();
    }

    /** 检查一项保护配置，零或负数使配置校验失败 */
    private int positiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("AI 保护上限必须大于零");
        }
        return limit;
    }

    /** 读取每日用量，文件损坏时保留文件并报告读取失败 */
    private DailyUsage loadUsage() {
        if (!Files.exists(quotaFile)) {
            return new DailyUsage();
        }
        try {
            if (Files.size(quotaFile) > QUOTA_FILE_BYTE_LIMIT) {
                throw new IOException("用量文件超过大小上限");
            }
            DailyUsage saved = mapper.readValue(quotaFile.toFile(), DailyUsage.class);
            if (saved == null || saved.accounts == null || saved.day == null || saved.total < 0) {
                throw new IOException("用量文件字段不完整");
            }
            if (saved.accounts.size() > TRACKED_IDENTITY_LIMIT) {
                throw new IOException("用量文件中的账号数量超出限制");
            }
            long total = 0;
            for (Integer count : saved.accounts.values()) {
                if (count == null || count < 0) {
                    throw new IOException("账号用量无效");
                }
                total = total + count;
            }
            if (total != saved.total) {
                throw new IOException("账号用量与总量不一致");
            }
            return saved;
        } catch (IOException error) {
            throw new IllegalStateException("AI 用量记录读取失败，请管理员检查文件", error);
        }
    }

    /** 获取计数表中的次数，未出现过的身份返回零 */
    private int count(Map<String, Integer> counts, String key) {
        Integer value = counts.get(key);
        if (value == null) {
            return 0;
        }
        return value;
    }

    /** 根据北京时间重置每日额度，根据经过的时长重置分钟频率 */
    private void refreshWindows(long now) {
        LocalDate today = LocalDate.now(clock.withZone(QUOTA_ZONE));
        String day = today.toString();
        if (!day.equals(dailyUsage.day)) {
            dailyUsage = new DailyUsage();
            dailyUsage.day = day;
        }
        if (now - minuteStartedAt >= MINUTE_MILLISECONDS || now < minuteStartedAt) {
            minuteStartedAt = now;
            minuteCount = 0;
            minuteAccounts.clear();
            minuteAddresses.clear();
        }
    }

    /** 原子检查并预扣一次调用，账号换对话也共用计数，失败调用同样占用额度 */
    public synchronized void begin(Integer userId, String address) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("无法确认请求来源");
        }
        refreshWindows(clock.millis());
        String account = userId.toString();
        if (activeRequests >= concurrentLimit) {
            throw new IllegalArgumentException("AI 服务并发已满，请稍后重试");
        }
        if (count(minuteAccounts, account) >= accountMinuteLimit
                || count(minuteAddresses, address) >= addressMinuteLimit
                || minuteCount >= globalMinuteLimit) {
            throw new IllegalArgumentException("提问较频繁，请一分钟后再试");
        }
        if (!minuteAddresses.containsKey(address) && minuteAddresses.size() >= TRACKED_IDENTITY_LIMIT) {
            throw new IllegalArgumentException("AI 服务较忙，请稍后重试");
        }
        reserveDailyCall(account);
        minuteAccounts.put(account, count(minuteAccounts, account) + 1);
        minuteAddresses.put(address, count(minuteAddresses, address) + 1);
        minuteCount = minuteCount + 1;
        Integer accountRequests = activeAccounts.get(userId);
        if (accountRequests == null) {
            accountRequests = 0;
        }
        activeAccounts.put(userId, accountRequests + 1);
        activeRequests = activeRequests + 1;
    }

    /** 在成功或异常后释放并发名额，每日调用次数保持已扣除状态 */
    public synchronized void finish(Integer userId) {
        Integer accountRequests = activeAccounts.get(userId);
        if (accountRequests == null) {
            return;
        }
        if (accountRequests == 1) {
            activeAccounts.remove(userId);
        } else {
            activeAccounts.put(userId, accountRequests - 1);
        }
        activeRequests = activeRequests - 1;
    }

    /** 文件锁串行处理共享文件的日额度，持锁进程结束后操作系统释放锁 */
    private void reserveDailyCall(String account) {
        Path lockFile = quotaFile.resolveSibling(quotaFile.getFileName() + ".lock");
        try {
            Files.createDirectories(quotaFile.getParent());
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    throw new IllegalArgumentException("AI 用量记录正在更新，请稍后重试");
                }
                try {
                    dailyUsage = loadUsage();
                    refreshWindows(clock.millis());
                    if (count(dailyUsage.accounts, account) >= accountDailyLimit
                            || dailyUsage.total >= globalDailyLimit) {
                        throw new IllegalArgumentException("今日 AI 调用额度已用完，请明天再试");
                    }
                    if (!dailyUsage.accounts.containsKey(account)
                            && dailyUsage.accounts.size() >= TRACKED_IDENTITY_LIMIT) {
                        throw new IllegalArgumentException("AI 服务较忙，请稍后重试");
                    }
                    dailyUsage.accounts.put(account, count(dailyUsage.accounts, account) + 1);
                    dailyUsage.total = dailyUsage.total + 1;
                    saveUsage();
                } finally {
                    lock.release();
                }
            }
        } catch (IOException | IllegalStateException error) {
            throw new IllegalArgumentException("AI 用量记录暂不可用，请联系管理员");
        }
    }

    /** 临时文件写完后原子替换正式记录，写入失败时保留上次完整记录 */
    private void saveUsage() throws IOException {
        Files.createDirectories(quotaFile.getParent());
        Path temporary = quotaFile.resolveSibling(quotaFile.getFileName() + ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), dailyUsage);
            Files.move(temporary, quotaFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 保存每日全站和账号调用次数，公开字段供 JSON 编码器读写 */
    public static class DailyUsage {
        public String day = "";
        public int total;
        public Map<String, Integer> accounts = new HashMap<>();
    }
}
