package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.PetCare;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按账户保存互动分，调用时沿用宠物房持有的账户锁 */
public interface PetCareRepository extends JpaRepository<PetCare, Integer> {
}
