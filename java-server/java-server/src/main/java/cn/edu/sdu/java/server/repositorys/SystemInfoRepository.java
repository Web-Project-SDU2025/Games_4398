package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.SystemInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemInfoRepository extends JpaRepository<SystemInfo,Integer> {
}
