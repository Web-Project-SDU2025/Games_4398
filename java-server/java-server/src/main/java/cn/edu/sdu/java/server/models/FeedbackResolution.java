package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 反馈处理实体类
 *
 * 保存管理员对问题反馈的处理决定和回复内容
 */
@Entity
@Table(name = "feedback_resolution")
@Getter
@Setter
public class FeedbackResolution {
    /** 消息ID，主键 */
    @Id
    @Column(length = 160)
    private String messageId;

    /** 处理决定：会修复、不修复、已解决等 */
    @Column(length = 20)
    private String decision;

    /** 处理此反馈的管理员ID */
    private Integer administratorId;

    /** 管理员的回复内容 */
    @Column(length = 2000)
    private String reply;

    /** 最后更新时间 */
    private Instant updatedAt;
}
