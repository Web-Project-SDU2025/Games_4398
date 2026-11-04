package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

/**
 * OptionItem 选项数据类
 * Integer id  数据项id
 * String value 数据项值
 * String label 数据值标题
 */
@Setter
@Getter
public class OptionItem {
    private Integer id;
    private String value;
    private String title;
    /** 创建空选项，供 JSON 工具填充字段 */
    public OptionItem(){

    }
    /** 保存选项编号、值和标题，允许值为空 */
    public OptionItem(Integer id, String value, String title){
        this.id = id;
        this.value = value;
        this.title = title;
    }

    /** 返回选项标题，空标题沿用当前空值 */
    public String toString(){
        return title;
    }

}
