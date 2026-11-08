package cn.edu.sdu.java.server.payload.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据响应类
 *
 * 前端HTTP请求返回数据对象
 * 包含返回代码、数据对象和消息
 */
@Setter
@Getter
public class DataResponse {
    /** 返回代码：0表示正确返回，1表示错误返回 */
    private Integer code;

    /** 返回数据对象 */
    private Object data;

    /** 返回正确或错误信息 */
    private String msg;

    /**
     * 无参构造函数
     * 提供JSON工具使用的空响应，字段随后由工具填充
     */
    public DataResponse(){

    }

    /**
     * 全参构造函数
     * 保存状态码、业务数据和提示，允许数据或提示为空
     */
    public DataResponse(Integer code, Object data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }
}
