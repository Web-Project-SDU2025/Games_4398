package cn.edu.sdu.java.server.models;
/**
 * 数据字典实体类
 *
 * 保存字典节点的基本信息
 * 父编号为空或零的节点作为根节点
 */
@Getter
@Setter
@Entity
@Table(	name = "dictionary",
        uniqueConstraints = {
        })
public class DictionaryInfo {
    /** 字典ID，主键，自动递增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 父节点ID，所属的字典类型ID */
    private Integer pid;

    /** 字典值 */
    @Size(max = FieldLimits.SHORT_TEXT)
    private String value;

    /** 字典名称 */
    @Size(max = FieldLimits.SHORT_TEXT)
    private String label;
}
