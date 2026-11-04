package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;

/*
 * MenuInfo 菜单表实体类  保存菜单的的基本信息信息， 数据库表名 menu
 * Integer id 菜单表  menu 主键 id
 * Integer pid  父节点ID  所属于的父菜单的id
 * String name 菜单名  记录FXML的文件名，用于导航加载FX面板
 * String name 菜单标题 菜单显示的标题
 */
import lombok.Getter;
import lombok.Setter;
/** 保存菜单标题、路由名和角色编号，路由名为空时表示分组 */
@Getter
@Setter
@Entity
@Table(	name = "menu",
        uniqueConstraints = {
        })
public class MenuInfo {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String userTypeIds;
    private Integer pid;

    @Size(max = FieldLimits.SHORT_TEXT)
    private String name;


    @Size(max = FieldLimits.SHORT_TEXT)
    private String title;

}
