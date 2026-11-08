package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息回执实体类
 *
 * 保存每个用户对消息的已读状态
 * 公告正文只保存一份，已读状态每人一条
 */
@Entity
@Table(name = "message_receipt")
@Getter
@Setter
public class MessageReceipt {
    /** 回执ID，主键 */
    @Id
    @Column(length = 220)
    private String receiptId;

    /** 用户ID */
    private Integer userId;

    /** 消息ID */
    @Column(length = 160)
    private String messageId;

    /** 已读时间 */
    private Instant readAt;
}
