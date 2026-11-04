package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.services.JwtService;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 使用内存账户验证公共工具，异常输入与正常输入分别断言 */
class CoreUtilityTests {
    // 测试用签名密钥，真实运行密钥继续由原配置提供
    private static final byte[] TEST_KEY = new byte[32];

    /** 创建测试令牌服务，有效期由用例传入 */
    private JwtService tokenService(long lifetime) {
        JwtService service = new JwtService();
        String encoded = Base64.getEncoder().encodeToString(TEST_KEY);
        ReflectionTestUtils.setField(service, "secretKey", encoded);
        ReflectionTestUtils.setField(service, "jwtExpiration", lifetime);
        return service;
    }

    /** 真实编码和解析验证账号绑定，其他账号匹配返回 false */
    @Test
    void jwtKeepsAccountAndSignature() {
        JwtService service = tokenService(60000);
        UserDetails account = new User("player", "hash", java.util.List.of());
        String token = service.generateToken(account);
        assertEquals("player", service.extractUsername(token));
        assertTrue(service.isTokenValid(token, account));
        UserDetails other = new User("other", "hash", java.util.List.of());
        assertFalse(service.isTokenValid(token, other));
        String[] parts = token.split("\\.");
        String changed = parts[0] + "." + parts[1] + ".invalid-signature";
        try {
            service.extractUsername(changed);
            fail("签名变化应触发解析异常");
        } catch (JwtException error) {
            assertNotNull(error);
        }
    }

    /** 过期令牌由解析器返回异常，认证层据此结束当前认证 */
    @Test
    void expiredJwtReturnsParsingFailure() {
        JwtService service = tokenService(-60000);
        UserDetails account = new User("player", "hash", java.util.List.of());
        String token = service.generateToken(account);
        try {
            service.isTokenValid(token, account);
            fail("过期令牌应触发解析异常");
        } catch (JwtException error) {
            assertNotNull(error);
        }
    }

    /** 验证码成功校验后立即移除，重复校验返回失败 */
    @Test
    void captchaIsConsumedOnce() {
        LoginControlUtil utility = LoginControlUtil.getInstance();
        Map<String, Object> result = utility.getValidateCodeDataMap();
        Integer id = (Integer) result.get("validateCodeId");
        String code = utility.getValidateCode(id);
        assertEquals(4, code.length());
        assertTrue(utility.checkAndRemoveCode(id, code));
        assertFalse(utility.checkAndRemoveCode(id, code));
    }

    /** 空参数表读取返回默认结果，随后写入会建立独立参数表 */
    @Test
    void missingRequestDataHasDefinedResults() {
        DataRequest request = new DataRequest();
        request.setData(null);
        assertNull(request.getString("name"));
        assertFalse(request.getBoolean("added"));
        assertTrue(request.getList("items").isEmpty());
        assertTrue(request.getMap("node").isEmpty());
        assertNull(request.getInteger("score"));
        request.add("score", "12");
        assertEquals(12, request.getInteger("score"));
        request.add("score", "invalid");
        assertNull(request.getInteger("score"));
    }
}
