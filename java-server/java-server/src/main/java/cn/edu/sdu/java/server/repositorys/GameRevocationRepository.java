package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.GameRevocation;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 读取有效撤回并保留操作历史，购买和撤回共用目标账户锁 */
public interface GameRevocationRepository extends JpaRepository<GameRevocation, String> {
    /** 有效撤回覆盖旧购买记录和原游戏库关联 */
    boolean existsByUserIdAndGameIdAndRepurchasedAtIsNull(Integer userId, Integer gameId);

    /** 重新购买后结束原撤回状态，已有操作记录继续保留 */
    @Modifying
    @Query("update GameRevocation entry set entry.repurchasedAt = :time "
        + "where entry.userId = :userId and entry.gameId = :gameId and entry.repurchasedAt is null")
    void markRepurchased(@Param("userId") Integer userId, @Param("gameId") Integer gameId,
        @Param("time") Instant time);
}
