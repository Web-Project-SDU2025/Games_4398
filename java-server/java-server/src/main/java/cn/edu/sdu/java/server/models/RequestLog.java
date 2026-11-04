package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;
import lombok.Getter;
import lombok.Setter;
/** 保存请求地址、账户和耗时，匿名请求的账户字段允许为空 */
@Getter
@Setter
@Entity
@Table(	name = "request_log",
        uniqueConstraints = {
        })
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = FieldLimits.DESCRIPTION)
    private String url;
    @Size(max = FieldLimits.USERNAME)
    private String username;
    @Size(max = FieldLimits.DATE_TIME)
    private String startTime;
    private Double requestTime;
}
