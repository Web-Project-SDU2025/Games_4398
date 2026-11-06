package cn.edu.sdu.java.server.repositorys;


import cn.edu.sdu.java.server.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
/*
 * User 数据操作接口，主要实现User数据的查询操作
 * Optional<User> findByUserName(String userName);  根据username查询获得Option<User>对象,  命名规范
 * Integer getMaxId()  user 表中的最大的user_id;    JPQL 注解
 * Optional<User> findByUserId(Integer userId);  根据userId查询获得Option<User>对象,  命名规范
 * Boolean existsByUserName(String userName);  判断userName的用户是否存在 命名规范
 */

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    /** 钱包操作先锁定账户行，初始化、购买和奖励按同一顺序串行提交 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from User account where account.userId = ?1")
    Optional<User> lockAccount(Integer userId);
    /** 按登录名读取账户，缺失时返回空 Optional */
    Optional<User> findByUserName(String userName);

    /** 按数据库角色读取管理员账户，用于分配问题反馈 */
    @Query("select account from User account where account.userType.name = ?1")
    List<User> findByUserTypeName(String roleName);

    /** 检查账号重复，参数通过仓库规则绑定 */
    Boolean existsByUserName(String userName);
    /** 统计指定时间后登录的账户，沿用原统计接口字段 */
    @Query(value="select count(*) from User where lastLoginTime >?1")
    Integer countLastLoginTime(String date);
    /** 按角色统计账户数，空表返回空分组列表 */
    @Query(value = "select userType.id, count(userId) from User group by userType.id" )
    List<?> getCountList();
}
