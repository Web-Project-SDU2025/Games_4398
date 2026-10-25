package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.Game;
import org.springframework.data.jpa.repository.JpaRepository;

/* Game 数据操作接口，用于保存和查询游戏。 */
public interface GameRepository extends JpaRepository<Game, Integer> {
    boolean existsByNum(String num);
}
