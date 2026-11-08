package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
/**
 * 选项列表响应类
 *
 * 返回前端的OptionItemList集合对象
 * 包含返回代码和选项列表
 */
@Setter
@Getter
public class OptionItemList {
    /** 返回代码：0表示正确，1表示错误 */
    private Integer code = 0;

    /** 选项列表 */
    private List<OptionItem> itemList;

    /**
     * 构造函数
     * 保存结果码和选项列表，空列表表示当前没有可选项
     */
    public OptionItemList(Integer code,List<OptionItem> itemList ){
        this.code = code;
        this.itemList = itemList;
    }
}
