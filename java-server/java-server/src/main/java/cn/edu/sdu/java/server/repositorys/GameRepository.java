package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.Game;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 游戏数据访问接口
 *
 * 提供游戏的保存和查询操作
 */
public interface GameRepository extends JpaRepository<Game, Integer> {
    /**
     * 检查游戏编号是否存在
     * 新增和修改编号时共用此查询
     */
    boolean existsByNum(String num);
}
