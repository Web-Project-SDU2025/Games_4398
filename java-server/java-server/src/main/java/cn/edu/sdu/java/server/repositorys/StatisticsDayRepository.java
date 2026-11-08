package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.StatisticsDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 日统计数据访问接口
 *
 * 读取日统计
 * 日期范围使用绑定参数
 */
public interface StatisticsDayRepository extends JpaRepository<StatisticsDay, Integer> {
    /**
     * 按日期范围查询统计
     * 按起止日期升序读取统计，范围内空日期保持缺省
     */
    @Query(value=" from StatisticsDay where day >=?1 and day <= ?2 order by day " )
    List<StatisticsDay> findListByDay(String startDay, String endDay);
}
