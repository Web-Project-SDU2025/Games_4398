package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.MenuInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
/**
 * 菜单信息数据访问接口
 *
 * 主要实现MenuInfo数据的查询操作
 * 菜单按用户角色和父节点查询
 */
public interface MenuInfoRepository extends JpaRepository<MenuInfo,Integer> {
    /**
     * 查询根菜单
     * 按角色筛选根菜单，空角色参数表示读取全部根菜单
     */
    @Query(value=" from MenuInfo where (pid is null or pid=0) and (?1='' or userTypeIds like %?1%)")
    List<MenuInfo> findByUserTypeIds(String userTypeIds);

    /**
     * 查询子菜单
     * 按父节点和角色筛选子菜单，空结果返回空列表
     */
    @Query(value=" from MenuInfo where pid =?2 and (?1='' or userTypeIds like %?1%)")
    List<MenuInfo> findByUserTypeIds(String userTypeIds, Integer pid);

    /**
     * 统计子菜单数
     * 统计子菜单数，删除父菜单前据此检查关联
     */
    int countMenuInfoByPid(Integer parentId);
}
