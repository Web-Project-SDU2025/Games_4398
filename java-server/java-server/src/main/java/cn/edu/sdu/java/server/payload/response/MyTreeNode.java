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
    public MyTreeNode(){

    }
    public MyTreeNode(Integer id, String value, String title,Integer isLeaf){
        this.id  = id;
        this.value = value;
        this.title = title;
        this.isLeaf = isLeaf;
//        this.label = id+"-" + title;
    }
    public String toString(){
        return label;
    }

}
