package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 每个账户保存一个整数余额，首次使用商城时在账户锁内初始化 */
@Entity
@Table(name = "blockcoin_wallet")
@Getter
@Setter
public class BlockcoinWallet {
    @Id
    private Integer userId;
    private Long balance;
}
