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
    public OptionItem(){

    }
    public OptionItem(Integer id, String value, String title){
        this.id = id;
        this.value = value;
        this.title = title;
    }

    public String toString(){
        return title;
    }

}
