package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
/**
 OptionItemList 发挥前端的OptionItemList集合对象
 Integer code 返回代码 0 正确 1 错误
 */
@Setter
@Getter
public class OptionItemList {
    private Integer code = 0;
    private List<OptionItem> itemList;

    public OptionItemList(Integer code,List<OptionItem> itemList ){
        this.code = code;
        this.itemList = itemList;
    }

}
