package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.BlockcoinWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 读写账户余额，并发操作先锁定对应账户行 */
public interface BlockcoinWalletRepository extends JpaRepository<BlockcoinWallet, Integer> {
    /** 汇总已创建钱包的实际余额，空表返回零 */
    @Query("select coalesce(sum(wallet.balance), 0) from BlockcoinWallet wallet")
    long totalBalance();
}
