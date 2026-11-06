package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存账户通知、公告和反馈，接收范围由消息类型与账户角色确定 */
@Entity
@Table(name = "site_message")
@Getter
@Setter
public class SiteMessage {
    @Id
    @Column(length = 160)
    private String messageId;
    private Integer recipientId;
    private Integer senderId;
    @Column(length = 60)
    private String senderName;
    @Column(length = 20)
    private String kind;
    @Column(length = 80)
    private String title;
    @Column(length = 2000)
    private String content;
    private Instant createdAt;
}
