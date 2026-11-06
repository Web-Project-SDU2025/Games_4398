package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.BlockcoinEntry;
import cn.edu.sdu.java.server.models.BlockcoinWallet;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.BlockcoinEntryRepository;
import cn.edu.sdu.java.server.repositorys.BlockcoinWalletRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.AdminQueryRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员查询账户流水和资金汇总，查询过程保留钱包与原始流水 */
@Service
public class AdminBlockcoinService {
    private final BlockcoinEntryRepository entries;
    private final BlockcoinWalletRepository wallets;
    private final UserRepository users;

    /** 使用现有账本和账户仓库，查询结果补充账号与昵称 */
    public AdminBlockcoinService(BlockcoinEntryRepository entries,
            BlockcoinWalletRepository wallets, UserRepository users) {
        this.entries = entries;
        this.wallets = wallets;
        this.users = users;
    }

    /** 读取整数参数，缺省使用传入默认值，小数和非法文字返回字段错误 */
    private Integer integer(DataRequest request, String name, Integer defaultValue) {
        String value = request.getString(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("查询编号或页码不正确");
        }
    }

    /** 查询文字统一去掉首尾空格，缺失字段按空值处理 */
    private String text(DataRequest request, String name) {
        String value = request.getString(name);
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    /** 起始日包含当天，结束日转换为次日零点，默认结束日使用今天 */
    private Instant dateBoundary(String value, boolean end) {
        if (value.isEmpty() && !end) {
            return Instant.EPOCH;
        }
        LocalDate date = LocalDate.now(AdminQueryRules.DATE_ZONE);
        if (!value.isEmpty()) {
            date = LocalDate.parse(value);
        }
        if (end) {
            date = date.plusDays(1);
        }
        return date.atStartOfDay(AdminQueryRules.DATE_ZONE).toInstant();
    }

    /** 按页内账户批量读取身份，账户删除后流水仍保留原账户编号 */
    private List<Map<String, Object>> rows(List<BlockcoinEntry> records) {
        Set<Integer> ids = new HashSet<Integer>();
        for (BlockcoinEntry entry : records) {
            ids.add(entry.getUserId());
        }
        Map<Integer, User> accounts = new HashMap<Integer, User>();
        for (User user : users.findAllById(ids)) {
            accounts.put(user.getUserId(), user);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (BlockcoinEntry entry : records) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("entryId", entry.getEntryId());
            row.put("userId", entry.getUserId());
            row.put("username", "");
            row.put("name", "");
            User user = accounts.get(entry.getUserId());
            if (user != null) {
                row.put("username", user.getUserName());
                row.put("name", user.getName());
            }
            row.put("amount", entry.getAmount());
            row.put("balanceAfter", entry.getBalanceAfter());
            row.put("reason", entry.getReason());
            row.put("gamePath", entry.getGamePath());
            row.put("createdAt", entry.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    /** 返回实际钱包余额，尚未创建钱包的账户使用空值 */
    private Long balance(Integer userId) {
        if (userId == null) {
            return wallets.totalBalance();
        }
        Optional<BlockcoinWallet> wallet = wallets.findById(userId);
        if (wallet.isEmpty()) {
            return null;
        }
        return wallet.get().getBalance();
    }

    /** 校验筛选并返回一页流水，累计收支按选中账户的完整账本计算 */
    @Transactional(readOnly = true)
    public DataResponse list(DataRequest request) {
        try {
            Integer userId = integer(request, "userId", null);
            Integer page = integer(request, "page", 1);
            Integer size = integer(request, "pageSize", AdminQueryRules.DEFAULT_PAGE_SIZE);
            if (page < 1 || size < 1 || size > AdminQueryRules.MAX_PAGE_SIZE) {
                return CommonMethod.getReturnMessageError("页码或每页条数不正确");
            }
            if (userId != null && (userId < 1 || !users.existsById(userId))) {
                return CommonMethod.getReturnMessageError("账户不存在");
            }
            String search = text(request, "search");
            if (search.length() > AdminQueryRules.MAX_SEARCH_LENGTH) {
                return CommonMethod.getReturnMessageError("搜索文字过长");
            }
            String direction = text(request, "direction");
            if (direction.isEmpty()) {
                direction = "all";
            }
            if (!List.of("all", "income", "expense").contains(direction)) {
                return CommonMethod.getReturnMessageError("请选择收支方向");
            }
            Instant start = dateBoundary(text(request, "startDate"), false);
            Instant end = dateBoundary(text(request, "endDate"), true);
            if (!start.isBefore(end)) {
                return CommonMethod.getReturnMessageError("开始日期应早于或等于结束日期");
            }
            PageRequest paging = PageRequest.of(page - 1, size);
            Page<BlockcoinEntry> records = entries.searchEntries(userId, direction, search, start, end, paging);
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("entries", rows(records.getContent()));
            result.put("total", records.getTotalElements());
            result.put("page", page);
            result.put("pageSize", size);
            result.put("balance", balance(userId));
            result.put("income", entries.totalIncome(userId));
            result.put("expense", entries.totalExpense(userId));
            return CommonMethod.getReturnData(result);
        } catch (IllegalArgumentException | java.time.DateTimeException error) {
            return CommonMethod.getReturnMessageError("查询参数或日期不正确");
        }
    }
}
