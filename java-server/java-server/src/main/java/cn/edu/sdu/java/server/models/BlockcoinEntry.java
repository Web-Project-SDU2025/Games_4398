package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存每次收入与支出，唯一业务编号让重试请求只记账一次 */
@Entity
@Table(name = "blockcoin_entry")
@Getter
@Setter
public class BlockcoinEntry {
    @Id
    private String entryId;
    private Integer userId;
    private Long amount;
    private Long balanceAfter;
    private String reason;
    private String gamePath;
    private Instant createdAt;
}
