package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.RecordVisibility;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按查看账户保存可见性，同一条游戏记录在不同账户中分别显示 */
public interface RecordVisibilityRepository extends JpaRepository<RecordVisibility, String> {
    /** 一次读取当前列表中的隐藏编号，查询次数与记录条数分开 */
    List<RecordVisibility> findByUserIdAndRecordIdIn(Integer userId, List<Integer> recordIds);
}
