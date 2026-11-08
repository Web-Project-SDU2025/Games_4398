package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.BilliardsRound;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 台球局数据访问接口
 *
 * 台球局号包含账户会话
 * 数据库主键隔离不同账户与不同盘的奖励
 */
public interface BilliardsRoundRepository extends JpaRepository<BilliardsRound, String> {
}
