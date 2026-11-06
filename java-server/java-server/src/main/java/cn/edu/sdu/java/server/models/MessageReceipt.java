package cn.edu.sdu.java.server.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 每个账户单独保存已读状态，公告正文只保存一份 */
@Entity
@Table(name = "message_receipt")
@Getter
@Setter
public class MessageReceipt {
    @Id
    @Column(length = 220)
    private String receiptId;
    private Integer userId;
    @Column(length = 160)
    private String messageId;
    private Instant readAt;
}
