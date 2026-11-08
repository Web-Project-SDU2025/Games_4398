package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.BlockcoinEntry;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 字块币流水数据访问接口
 *
 * 查询唯一业务流水与当前账户的最近收支
 */
public interface BlockcoinEntryRepository extends JpaRepository<BlockcoinEntry, String> {
    /**
     * 按时间倒序查询流水
     * 按时间倒序读取指定账户的流水，条数由业务规则限制
     */
    List<BlockcoinEntry> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable page);

    /**
     * 搜索流水记录
     * 按账户、方向、原因和日期分页查询，编号为同一时刻的流水提供稳定顺序
     */
    @Query("select entry from BlockcoinEntry entry "
        + "where (:userId is null or entry.userId = :userId) "
        + "and (:direction = 'all' or (:direction = 'income' and entry.amount > 0) "
        + "or (:direction = 'expense' and entry.amount < 0)) "
        + "and lower(entry.reason) like lower(concat('%', :search, '%')) "
        + "and entry.createdAt >= :start and entry.createdAt < :end "
        + "order by entry.createdAt desc, entry.entryId desc")
    Page<BlockcoinEntry> searchEntries(@Param("userId") Integer userId,
        @Param("direction") String direction, @Param("search") String search,
        @Param("start") Instant start, @Param("end") Instant end, Pageable page);

    /**
     * 累计收入
     * 累计收入按账户汇总，未指定账户时汇总全部流水
     */
    @Query("select coalesce(sum(entry.amount), 0) from BlockcoinEntry entry "
        + "where entry.amount > 0 and (:userId is null or entry.userId = :userId)")
    long totalIncome(@Param("userId") Integer userId);

    /**
     * 累计支出
     * 累计支出返回正数，空账本返回零
     */
    @Query("select coalesce(sum(-entry.amount), 0) from BlockcoinEntry entry "
        + "where entry.amount < 0 and (:userId is null or entry.userId = :userId)")
    long totalExpense(@Param("userId") Integer userId);
}
