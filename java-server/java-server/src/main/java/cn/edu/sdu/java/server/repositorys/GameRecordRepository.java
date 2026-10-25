package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/* GameRecord 数据操作接口，用于查询和删除游戏记录。 */
public interface GameRecordRepository extends JpaRepository<GameRecord, Integer> {
    List<GameRecord> findAllByOrderByPlayedAtDesc();
    List<GameRecord> findByUserUserIdOrderByPlayedAtDesc(Integer userId);
    boolean existsByGameGameId(Integer gameId);
    void deleteByUserUserId(Integer userId);
}
