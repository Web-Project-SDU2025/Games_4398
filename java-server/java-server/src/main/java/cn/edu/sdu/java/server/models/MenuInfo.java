package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import cn.edu.sdu.java.server.util.FieldLimits;

/**
 * 菜单信息实体类
 *
 * 保存菜单标题、路由名和角色权限
 * 路由名为空时表示菜单分组
 */
@Getter
@Setter
@Entity
@Table(	name = "menu",
        uniqueConstraints = {
        })
public class MenuInfo {
    /** 菜单ID，主键 */
    @Id
    private Integer id;

    /** 允许访问的角色ID列表 */
    private String userTypeIds;

    /** 父节点ID，所属的父菜单ID */
    private Integer pid;

    /** 路由名称，记录页面路径，用于导航加载 */
    @Size(max = FieldLimits.SHORT_TEXT)
    private String name;

    /** 菜单显示标题 */
    @Size(max = FieldLimits.SHORT_TEXT)
    private String title;

}
