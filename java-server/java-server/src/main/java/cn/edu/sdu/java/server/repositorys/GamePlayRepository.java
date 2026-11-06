package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.GamePlay;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 保存通用游戏会话，账户行锁统一保护同一账户的并发写入 */
public interface GamePlayRepository extends JpaRepository<GamePlay, String> {
    /** 按记录编号读取关卡、结果和自动记录标记 */
    Optional<GamePlay> findByRecordId(Integer recordId);
    /** 统计短时间内开始的游戏，限制重复创建 */
    long countByUserIdAndStartedAtAfter(Integer userId, Instant after);
    /** 只统计仍在同步的实例，超时会话保留历史并释放并发名额 */
    long countByUserIdAndEndedAtIsNullAndUpdatedAtAfter(Integer userId, Instant after);
    /** 撤回游戏时结束该账户仍未结算的会话 */
    List<GamePlay> findByUserIdAndGameIdAndEndedAtIsNull(Integer userId, Integer gameId);
}
