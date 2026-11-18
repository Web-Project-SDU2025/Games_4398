package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.PetPurchaseBatch;
import org.springframework.data.jpa.repository.JpaRepository;

/** 以账户编号读取和保存小屋的待通知消费 */
public interface PetPurchaseBatchRepository extends JpaRepository<PetPurchaseBatch, Integer> {
}
