package cn.edu.sdu.java.server.repositorys;


import cn.edu.sdu.java.server.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
/**
 * 用户数据访问接口
 *
 * 提供用户账户的查询和锁定操作
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    /**
     * 锁定用户账户
     * 钱包操作前先锁定账户行，初始化、购买和奖励按同一顺序串行提交
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from User account where account.userId = ?1")
    Optional<User> lockAccount(Integer userId);

    /**
     * 按用户名查找
     * 按登录名读取账户，缺失时返回空Optional
     */
    Optional<User> findByUserName(String userName);

    /**
     * 按角色查找管理员
     * 按数据库角色读取管理员账户，用于分配问题反馈
     */
    @Query("select account from User account where account.userType.name = ?1")
    List<User> findByUserTypeName(String roleName);

    /**
     * 检查用户名是否存在
     * 注册时检查账号重复
     */
    Boolean existsByUserName(String userName);

    /**
     * 统计最近登录用户数
     * 统计指定时间后登录的账户数量
     */
    @Query(value="select count(*) from User where lastLoginTime >?1")
    Integer countLastLoginTime(String date);

    /**
     * 按角色统计用户数
     * 按角色分组统计账户数，空表返回空列表
     */
    @Query(value = "select userType.id, count(userId) from User group by userType.id" )
    List<?> getCountList();
}
