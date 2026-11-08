package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.DictionaryInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
/**
 * 数据字典数据访问接口
 *
 * 主要实现DictionaryInfo数据的查询操作
 * 支持树形结构的字典数据查询
 */
public interface DictionaryInfoRepository extends JpaRepository<DictionaryInfo,Integer> {
    /**
     * 查询根节点列表
     * 查询根节点，父编号为空或零时归为根节点
     */
    @Query(value= " from DictionaryInfo where pid is null or pid =0 ")
    List<DictionaryInfo> findRootList();

    /**
     * 查询子节点列表
     * 查询指定父节点的子项，空结果返回空列表
     */
    List<DictionaryInfo> findByPid(Integer pid);

    /**
     * 按父节点值查询选项
     * 按父节点的字典值读取选项，参数通过占位符绑定
     */
    @Query(value = "select d.* from dictionary d, dictionary f where f.id = d.pid and f.value = ?1", nativeQuery = true)
    List<DictionaryInfo>getDictionaryInfoList(String code);

    /**
     * 统计子节点数
     * 统计子项数，零表示该节点可以进入删除检查
     */
    int countDictionaryInfoByPid(Integer parentId);
}
