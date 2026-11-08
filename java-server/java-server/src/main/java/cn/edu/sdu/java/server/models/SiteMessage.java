package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 站内消息实体类
 *
 * 保存通知、公告和反馈消息
 * 接收范围由消息类型和用户角色决定
 */
@Entity
@Table(name = "site_message")
@Getter
@Setter
public class SiteMessage {
    /** 消息ID，主键 */
    @Id
    @Column(length = 160)
    private String messageId;

    /** 接收者ID，null表示广播消息 */
    private Integer recipientId;

    /** 发送者ID，系统消息为null */
    private Integer senderId;

    /** 发送者名称 */
    @Column(length = 60)
    private String senderName;

    /** 消息类型：通知、公告、反馈等 */
    @Column(length = 20)
    private String kind;

    /** 消息标题 */
    @Column(length = 80)
    private String title;

    /** 消息内容 */
    @Column(length = 2000)
    private String content;

    /** 创建时间 */
    private Instant createdAt;
}
