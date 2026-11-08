package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 选项数据类
 *
 * 封装选项的编号、值和标题
 * 用于下拉列表和选择框数据传输
 */
@Setter
@Getter
public class OptionItem {
    /** 数据项ID */
    private Integer id;

    /** 数据项值 */
    private String value;

    /** 数据值标题 */
    private String title;

    /**
     * 无参构造函数
     * 创建空选项，供JSON工具填充字段
     */
    public OptionItem(){

    }

    /**
     * 全参构造函数
     * 保存选项编号、值和标题，允许值为空
     */
    public OptionItem(Integer id, String value, String title){
        this.id = id;
        this.value = value;
        this.title = title;
    }

    /**
     * 转换为字符串
     * 返回选项标题，空标题沿用当前空值
     */
    public String toString(){
        return title;
    }
}
