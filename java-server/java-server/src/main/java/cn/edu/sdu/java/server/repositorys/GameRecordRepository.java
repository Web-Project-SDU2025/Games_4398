package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/* GameRecord 数据操作接口，用于查询和删除游戏记录 */
public interface GameRecordRepository extends JpaRepository<GameRecord, Integer> {
    /** 按时间倒序读取全部记录，空表返回空列表 */
    List<GameRecord> findAllByOrderByPlayedAtDesc();
    /** 按账户筛选记录，空结果返回空列表 */
    List<GameRecord> findByUserUserIdOrderByPlayedAtDesc(Integer userId);
    /** 检查游戏是否存在记录，删除游戏前据此检查关联 */
    boolean existsByGameGameId(Integer gameId);
    /** 删除指定账户的记录，与账户删除事务一起提交 */
    void deleteByUserUserId(Integer userId);
}
