package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.GameRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * 游戏记录数据访问接口
 *
 * 提供游戏记录的查询和删除操作
 */
public interface GameRecordRepository extends JpaRepository<GameRecord, Integer> {
    /**
     * 查询全部记录
     * 按时间倒序返回，空表返回空列表
     */
    List<GameRecord> findAllByOrderByPlayedAtDesc();

    /**
     * 按用户查询记录
     * 按时间倒序返回该用户的记录
     */
    List<GameRecord> findByUserUserIdOrderByPlayedAtDesc(Integer userId);

    /**
     * 检查游戏是否有记录
     * 删除游戏前检查是否有关联记录
     */
    boolean existsByGameGameId(Integer gameId);

    /**
     * 删除游戏的全部记录
     * 与游戏删除在同一事务中提交
     */
    void deleteByGameGameId(Integer gameId);

    /**
     * 删除用户的全部记录
     * 与用户删除在同一事务中提交
     */
    void deleteByUserUserId(Integer userId);

    /**
     * 统计用户记录数
     * 账户列表用此展示活动数量
     */
    long countByUserUserId(Integer userId);
}
