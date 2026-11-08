package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 字块币钱包
 *
 * 每个用户有一个钱包，保存当前余额
 * 首次使用商城时会在账户锁保护下自动创建并发放新手礼包
 */
@Entity
@Table(name = "blockcoin_wallet")
@Getter
@Setter
public class BlockcoinWallet {
    /** 用户ID，也是主键 */
    @Id
    private Integer userId;

    /** 当前余额，用整数避免浮点精度问题 */
    private Long balance;
}
