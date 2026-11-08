package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;

import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/**
 * 日统计实体类
 *
 * 以日期文字为主键保存每日统计数据
 * 缺失的日期由查询列表自然跳过
 */
@Getter
@Setter
@Entity
@Table(	name = "statistics_day",
        uniqueConstraints = {
        })
public class StatisticsDay {
    /** 日期主键，格式为yyyyMMdd */
    @Id
    @Size(max = FieldLimits.DATE)
    private String  day;

    /** 登录次数 */
    private Integer loginCount;

    /** 请求次数 */
    private Integer requestCount;

    /** 创建次数 */
    private Integer createCount;

    /** 修改次数 */
    private Integer modifyCount;

}
