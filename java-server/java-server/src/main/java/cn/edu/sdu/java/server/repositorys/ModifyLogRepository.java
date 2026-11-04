package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.ModifyLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** 保存修改日志，数据库约束异常由调用服务处理 */
public interface ModifyLogRepository extends JpaRepository<ModifyLog,Integer>{

}
