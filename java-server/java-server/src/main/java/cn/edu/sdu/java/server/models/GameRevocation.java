package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存每次游戏撤回，重新购买后保留原操作记录并标记新购买时间 */
@Entity
@Table(name = "game_revocation")
@Getter
@Setter
public class GameRevocation {
    @Id
    private String revocationId;
    private Integer userId;
    private Integer gameId;
    private Integer operatorId;
    private String reason;
    private Instant createdAt;
    private Instant repurchasedAt;
}
