package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * MyTreeNode  树节点类
 * Integer id 节点ID 菜单或数据字典的主键ID
 * String value; 节点值
 * String label  节点标题
 * Integer pid 父节点
 * List<MyTreeNode> childList 子节点对象列表
 */
@Setter
@Getter
public class MyTreeNode {
    private Integer id;
    private String value;
    private String label;
    private String title;
    private Integer pid;
    private Integer isLeaf;
    private String userTypeIds;
    private String parentTitle;
    private List<MyTreeNode> children;
    /** 创建空树节点，供 JSON 工具逐项填充字段 */
    public MyTreeNode(){

    }
    /** 保存节点字段，子列表由树形业务统一装配 */
    public MyTreeNode(Integer id, String value, String title,Integer isLeaf){
        this.id  = id;
        this.value = value;
        this.title = title;
        this.isLeaf = isLeaf;
    }
    /** 返回显示标签，空标签沿用当前节点的空值 */
    public String toString(){
        return label;
    }

}
