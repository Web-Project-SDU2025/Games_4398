package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserType 数据操作接口，主要实现UserType数据的查询操作
 * 根据角色名称查找 UserType，角色名称使用字符串保存
 */
@Repository
public interface UserTypeRepository extends JpaRepository<UserType, Integer> {
    /** 按角色名读取类型，缺失时沿用仓库的空值结果 */
    UserType findByName(String name);
}
