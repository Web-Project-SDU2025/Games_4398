package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/**
 * 系统配置实体类
 *
 * 保存系统配置项的名称和值
 * 启动时由系统服务读取
 */
@Getter
@Setter
@Entity
@Table(	name = "system_info",
        uniqueConstraints = {
        })
    public class SystemInfo {
    /** 配置ID，主键，自动递增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 配置名称 */
    @Size(max = FieldLimits.SHORT_TEXT)
    String name;

    /** 配置值 */
    @Size(max = FieldLimits.SHORT_TEXT)
    String value;

    /** 配置说明 */
    @Size(max = FieldLimits.DESCRIPTION)
    String des;

}
