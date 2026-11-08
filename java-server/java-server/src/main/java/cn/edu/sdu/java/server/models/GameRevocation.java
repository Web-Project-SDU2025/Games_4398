package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 游戏撤回实体类
 *
 * 保存每次游戏撤回记录
 * 重新购买后保留原操作记录并标记新购买时间
 */
@Entity
@Table(name = "game_revocation")
@Getter
@Setter
public class GameRevocation {
    /** 撤回ID，主键 */
    @Id
    private String revocationId;

    /** 用户ID */
    private Integer userId;

    /** 游戏ID */
    private Integer gameId;

    /** 操作员ID */
    private Integer operatorId;

    /** 撤回原因 */
    private String reason;

    /** 创建时间 */
    private Instant createdAt;

    /** 重新购买时间 */
    private Instant repurchasedAt;
}
