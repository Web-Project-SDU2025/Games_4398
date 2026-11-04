package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.SystemInfo;
import org.springframework.data.jpa.repository.JpaRepository;

/** 读取系统配置，空表由启动服务整理为空配置 */
public interface SystemInfoRepository extends JpaRepository<SystemInfo,Integer> {
}
