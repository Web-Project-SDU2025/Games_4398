package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/**
 * 请求日志实体类
 *
 * 保存请求地址、账户和耗时
 * 匿名请求的账户字段允许为空
 */
@Getter
@Setter
@Entity
@Table(	name = "request_log",
        uniqueConstraints = {
        })
public class RequestLog {
    /** 日志ID，主键，自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 请求URL */
    @Size(max = FieldLimits.DESCRIPTION)
    private String url;

    /** 用户名 */
    @Size(max = FieldLimits.USERNAME)
    private String username;

    /** 开始时间 */
    @Size(max = FieldLimits.DATE_TIME)
    private String startTime;

    /** 请求耗时 */
    private Double requestTime;
}
