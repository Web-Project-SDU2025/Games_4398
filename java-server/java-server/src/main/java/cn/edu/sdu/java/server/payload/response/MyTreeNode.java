package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 树节点类
 *
 * 用于表示菜单或数据字典的树形结构
 * 支持父子关系和递归子节点列表
 */
@Setter
@Getter
public class MyTreeNode {
    /** 节点ID，菜单或数据字典的主键ID */
    private Integer id;

    /** 节点值 */
    private String value;

    /** 节点标签 */
    private String label;

    /** 节点标题 */
    private String title;

    /** 父节点ID */
    private Integer pid;

    /** 是否为叶子节点 */
    private Integer isLeaf;

    /** 用户类型ID列表 */
    private String userTypeIds;

    /** 父节点标题 */
    private String parentTitle;

    /** 子节点列表 */
    private List<MyTreeNode> children;

    /**
     * 无参构造函数
     * 创建空树节点，供JSON工具逐项填充字段
     */
    public MyTreeNode(){

    }

    /**
     * 参数构造函数
     * 保存节点字段，子列表由树形业务统一装配
     */
    public MyTreeNode(Integer id, String value, String title,Integer isLeaf){
        this.id  = id;
        this.value = value;
        this.title = title;
        this.isLeaf = isLeaf;
    }

    /**
     * 转换为字符串
     * 返回显示标签，空标签沿用当前节点的空值
     */
    public String toString(){
        return label;
    }
}
