package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 按原资料条件更新默认名称
     * 二进制比较保留仅大小写或空格不同的自定义资料
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE game SET name = :name
            WHERE game_id = :gameId
              AND CAST(num AS BINARY) = CAST(:num AS BINARY)
              AND CAST(game_path AS BINARY) = CAST(:path AS BINARY)
              AND CAST(name AS BINARY) = CAST(:previousName AS BINARY)
            """, nativeQuery = true)
    void renameIfUnchanged(@Param("gameId") Integer gameId, @Param("num") String num,
            @Param("path") String path, @Param("previousName") String previousName, @Param("name") String name);
}
