package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存管理员对单条问题反馈的处理决定和回复 */
@Entity
@Table(name = "feedback_resolution")
@Getter
@Setter
public class FeedbackResolution {
    @Id
    @Column(length = 160)
    private String messageId;
    @Column(length = 20)
    private String decision;
    private Integer administratorId;
    @Column(length = 2000)
    private String reply;
    private Instant updatedAt;
}
