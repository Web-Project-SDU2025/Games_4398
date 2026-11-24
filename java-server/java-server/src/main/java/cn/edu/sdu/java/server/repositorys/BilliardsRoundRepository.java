package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.BilliardsRound;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 台球局数据访问接口
 *
 * 台球局号包含账户会话
 * 数据库主键隔离不同账户与不同盘的奖励
 */
public interface BilliardsRoundRepository extends JpaRepository<BilliardsRound, String> {
    /**
     * 查询当前账号最近结算的人机局
     * 按单盘的最终击球时间排序，跳过未结束和本地双人局
     */
    @Query(value = """
            SELECT r.*
            FROM billiards_round r
            JOIN game_play p ON p.play_id = r.play_id
            WHERE p.user_id = :userId AND p.mode = 'official'
              AND r.last_reward IS NOT NULL
              AND JSON_CONTAINS(IF(JSON_VALID(r.state_json), r.state_json, '{}'),
                    '{"aiMatch": true, "ended": true}')
              AND JSON_UNQUOTE(JSON_EXTRACT(
                    IF(JSON_VALID(r.state_json), r.state_json, '{}'), '$.winner')) IN ('0', '1')
            ORDER BY r.updated_at DESC, r.round_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<BilliardsRound> findLatestCompletedSingle(@Param("userId") Integer userId);
}
