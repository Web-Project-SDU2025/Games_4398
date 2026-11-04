package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/** 保存系统配置名称和值，启动时由系统服务读取 */
@Getter
@Setter
@Entity
@Table(	name = "system_info",
        uniqueConstraints = {
        })
    public class SystemInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = FieldLimits.SHORT_TEXT)
    String name;
    @Size(max = FieldLimits.SHORT_TEXT)
    String value;
    @Size(max = FieldLimits.DESCRIPTION)
    String des;

}
