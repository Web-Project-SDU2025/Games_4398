package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/** 保存数据修改日志，字段长度由实体校验约束 */
@Getter
@Setter
@Entity
@Table(	name = "modify_log",
        uniqueConstraints = {
        })
public class ModifyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = FieldLimits.LOG_TYPE)
    private String type;
    @Size(max = FieldLimits.TABLE_NAME)
    private String tableName;
    @Size(max = FieldLimits.LOG_CONTENT)
    private String info;
    @Size(max = FieldLimits.DATE_TIME)
    private String operateTime;
    private Integer operatorId;

}
