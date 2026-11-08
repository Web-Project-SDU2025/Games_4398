package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/**
 * 修改日志实体类
 *
 * 保存数据修改日志
 * 字段长度由实体校验约束
 */
@Getter
@Setter
@Entity
@Table(	name = "modify_log",
        uniqueConstraints = {
        })
public class ModifyLog {
    /** 日志ID，主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 操作类型 */
    @Size(max = FieldLimits.LOG_TYPE)
    private String type;

    /** 表名 */
    @Size(max = FieldLimits.TABLE_NAME)
    private String tableName;

    /** 日志内容 */
    @Size(max = FieldLimits.LOG_CONTENT)
    private String info;

    /** 操作时间 */
    @Size(max = FieldLimits.DATE_TIME)
    private String operateTime;

    /** 操作员ID */
    private Integer operatorId;
}
