package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 字块币流水记录
 *
 * 保存每笔收入和支出的详细信息
 * 用唯一业务编号防止重复记账，比如同一个购买请求点两次只会扣一次钱
 */
@Entity
@Table(name = "blockcoin_entry")
@Getter
@Setter
public class BlockcoinEntry {
    /** 业务编号，同一个编号只能入账一次 */
    @Id
    private String entryId;

    /** 账户ID，标识这笔流水属于谁 */
    private Integer userId;

    /** 金额，正数表示收入，负数表示支出 */
    private Long amount;

    /** 本次操作后的余额 */
    private Long balanceAfter;

    /** 流水原因，比如"购买植物大战僵尸"或"通关奖励" */
    private String reason;

    /** 关联的游戏路径，购买和奖励会填这个字段 */
    private String gamePath;

    /** 创建时间 */
    private Instant createdAt;
}
