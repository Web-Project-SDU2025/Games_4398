package cn.edu.sdu.java.server.util;

/**
 * AI图片识别规则配置
 *
 * 管理图片输入的数量、体积和尺寸参数
 * 入口验证和解码校验共用这些限制
 */
public final class AiImageRules {
    /** 一次提问最多携带的图片数量 */
    public static final int MAX_IMAGES = 4;

    /** 压缩后单张图片的字节上限 */
    public static final int MAX_IMAGE_BYTES = 262144;

    /** 压缩后全部图片的总字节上限 */
    public static final int MAX_TOTAL_BYTES = 786432;

    /** 图片请求体包括Base64和JSON的总字节上限 */
    public static final int MAX_REQUEST_BYTES = 1572864;

    /** 图片宽高的像素上限 */
    public static final int MAX_EDGE = 1280;

    /** 云端会话的最多数量 */
    public static final int SESSION_LIMIT = 2000;

    /** 云端会话有效期（毫秒） */
    public static final long SESSION_LIFETIME = 3300000;

    /** 会话达到此轮数后重新开始上下文 */
    public static final int SESSION_TURNS = 20;

    /** 单次回答的最长等待秒数 */
    public static final int TIMEOUT_SECONDS = 60;

    /** 网络连接超时秒数 */
    public static final int CONNECT_SECONDS = 10;

    /** 工具类禁止实例化 */
    private AiImageRules() {
    }
}
