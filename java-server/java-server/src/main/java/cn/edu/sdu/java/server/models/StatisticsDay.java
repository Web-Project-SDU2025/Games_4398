package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;

import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/** 以日期文字为主键保存日统计，缺失日期由查询列表自然跳过 */
@Getter
@Setter@Entity
@Table(	name = "statistics_day",
        uniqueConstraints = {
        })
public class StatisticsDay {
    @Id
    @Size(max = FieldLimits.DATE)
    private String  day;
    private Integer loginCount;
    private Integer requestCount;
    private Integer createCount;
    private Integer modifyCount;

}
