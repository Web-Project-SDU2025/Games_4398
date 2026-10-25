package cn.edu.sdu.java.server.repositorys;


import cn.edu.sdu.java.server.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
    Optional<User> findByUserName(String userName);


    Boolean existsByUserName(String userName);
    @Query(value="select count(*) from User where lastLoginTime >?1")
    Integer countLastLoginTime(String date);
    @Query(value = "select userType.id, count(userId) from User group by userType.id" )
    List<?> getCountList();
}
