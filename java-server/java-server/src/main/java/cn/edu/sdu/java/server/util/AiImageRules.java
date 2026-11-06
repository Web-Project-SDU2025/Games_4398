package cn.edu.sdu.java.server.util;

/** 图片输入的数量、体积与尺寸参数，入口和解码校验共用 */
public final class AiImageRules {
    /** 一次问题最多携带的图片数量 */
    public static final int MAX_IMAGES = 4;
    /** 压缩后单张图片的字节上限 */
    public static final int MAX_IMAGE_BYTES = 262144;
    /** 压缩后全部图片的字节上限 */
    public static final int MAX_TOTAL_BYTES = 786432;
    /** 图片请求体包括 Base64 和 JSON 的总字节上限 */
    public static final int MAX_REQUEST_BYTES = 1572864;
    /** 图片宽高的像素上限 */
    public static final int MAX_EDGE = 1280;
    /** 图片云端会话的最多数量 */
    public static final int SESSION_LIMIT = 2000;
    /** 图片云端会话有效期，单位为毫秒 */
    public static final long SESSION_LIFETIME = 3300000;
    /** 图片会话达到此轮数后重新开始上下文 */
    public static final int SESSION_TURNS = 20;
    /** 单次回答的最长等待秒数 */
    public static final int TIMEOUT_SECONDS = 60;
    /** 网络连接超时秒数 */
    public static final int CONNECT_SECONDS = 10;

    /** 参数通过类名读取 */
    private AiImageRules() {
    }
}
