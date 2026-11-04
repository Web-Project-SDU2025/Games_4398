package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.DictionaryInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
/*
 * DictionaryInfo 数据操作接口，主要实现DictionaryInfo数据的查询操作
 * Integer getMaxId()  dictionary 表中的最大的id;    JPQL 注解
 * List<DictionaryInfo> findRootList();  查询获取顶层的数据字典（所有数据字典的code集合） JPQL 注解
 * List<DictionaryInfo> findByPid(Integer pid); 查询获取pid的子数据字典集合  命名规范
 * List<DictionaryInfo>getDictionaryInfoList(String code);查询父节点字典值为code的所有的数据字典集合  SQL原生查询
 */
public interface DictionaryInfoRepository extends JpaRepository<DictionaryInfo,Integer> {
    /** 查询根节点，父编号为空或零时归为根节点 */
    @Query(value= " from DictionaryInfo where pid is null or pid =0 ")
    List<DictionaryInfo> findRootList();

    /** 查询指定父节点的子项，空结果返回空列表 */
    List<DictionaryInfo> findByPid(Integer pid);

    /** 按父节点的字典值读取选项，参数通过占位符绑定 */
    @Query(value = "select d.* from dictionary d, dictionary f where f.id = d.pid and f.value = ?1", nativeQuery = true)
    List<DictionaryInfo>getDictionaryInfoList(String code);
    /** 统计子项数，零表示该节点可以进入删除检查 */
    int countDictionaryInfoByPid(Integer parentId);


}
