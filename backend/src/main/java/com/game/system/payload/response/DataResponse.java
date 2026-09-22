package com.game.system.payload.response;

/**
 * 统一数据响应封装类
 * 用于返回给前端的数据格式
 */
public class DataResponse {

    private Integer code;  // 0-成功 1-失败
    private Object data;   // 返回数据
    private String msg;    // 返回信息

    public DataResponse() {
    }

    public DataResponse(Integer code, Object data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    /**
     * 成功响应（带数据和消息）
     */
    public static DataResponse success(Object data, String msg) {
        return new DataResponse(0, data, msg);
    }

    /**
     * 成功响应（仅带数据）
     */
    public static DataResponse success(Object data) {
        return new DataResponse(0, data, null);
    }

    /**
     * 成功响应（仅带消息）
     */
    public static DataResponse success(String msg) {
        return new DataResponse(0, null, msg);
    }

    /**
     * 成功响应（无数据无消息）
     */
    public static DataResponse success() {
        return new DataResponse(0, null, null);
    }

    /**
     * 失败响应
     */
    public static DataResponse error(String msg) {
        return new DataResponse(1, null, msg);
    }

    /**
     * 自定义响应
     */
    public static DataResponse custom(Integer code, Object data, String msg) {
        return new DataResponse(code, data, msg);
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "DataResponse{" +
                "code=" + code +
                ", data=" + data +
                ", msg='" + msg + '\'' +
                '}';
    }
}
