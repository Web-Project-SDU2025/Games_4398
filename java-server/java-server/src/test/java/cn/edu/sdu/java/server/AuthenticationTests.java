package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Base64;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationTests {
    @Autowired
    private MockMvc client;
    @Autowired
    private ObjectMapper mapper;

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");
        Map<String, Object> captcha = LoginControlUtil.getInstance().getValidateCodeDataMap();
        Integer captchaId = (Integer) captcha.get("validateCodeId");
        request.setValidateCodeId(captchaId);
        request.setValidateCode(LoginControlUtil.getInstance().getValidateCode(captchaId));
        return request;
    }

    private ResultActions send(String path, Object request) throws Exception {
        String body = mapper.writeValueAsString(request);
        return client.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void captchaImageIsValidPng() throws Exception {
        MvcResult result = send("/auth/getValidateCode", new DataRequest()).andReturn();
        String response = result.getResponse().getContentAsString();
        String image = mapper.readTree(response).path("data").path("img").asText();
        assertTrue(image.startsWith("data:image/png;base64,"));
        String base64 = image.substring(image.indexOf(',') + 1);
        byte[] bytes = Base64.getDecoder().decode(base64);
        assertEquals(0x89, bytes[0] & 0xff);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes)));
    }

    @Test
    void loginRequiresCaptchaAndRejectsReuse() throws Exception {
        LoginRequest missing = new LoginRequest();
        missing.setUsername("admin");
        missing.setPassword("123456");
        send("/auth/login", missing).andExpect(status().isBadRequest());
        LoginRequest wrong = loginRequest();
        wrong.setValidateCode("wrong");
        send("/auth/login", wrong).andExpect(status().isBadRequest());
        LoginRequest valid = loginRequest();
        send("/auth/login", valid).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        send("/auth/login", valid).andExpect(status().isBadRequest());
    }

    @Test
    void badPasswordReturnsReadableMessage() throws Exception {
        LoginRequest request = loginRequest();
        request.setPassword("incorrect-password");
        send("/auth/login", request).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.msg").value("账号或密码不正确"));
    }

    @Test
    void registrationRequiresCaptchaAndPreventsDuplicates() throws Exception {
        DataRequest request = new DataRequest();
        request.add("username", "auth_check");
        request.add("password", "Test123!");
        request.add("name", "注册验证");
        send("/auth/registerUser", request).andExpect(jsonPath("$.code").value(1));
        LoginRequest captcha = loginRequest();
        request.add("validateCodeId", captcha.getValidateCodeId());
        request.add("validateCode", captcha.getValidateCode());
        send("/auth/registerUser", request).andExpect(jsonPath("$.code").value(0));
        LoginRequest registeredLogin = loginRequest();
        registeredLogin.setUsername("auth_check");
        registeredLogin.setPassword("Test123!");
        MvcResult loginResult = send("/auth/login", registeredLogin).andReturn();
        assertEquals(200, loginResult.getResponse().getStatus());
        String loginBody = loginResult.getResponse().getContentAsString();
        String token = mapper.readTree(loginBody).path("token").asText();
        assertFalse(token.isBlank());
        client.perform(post("/api/base/getMenuList")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"data\":{}}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNotEmpty());
        LoginRequest duplicateCaptcha = loginRequest();
        request.add("validateCodeId", duplicateCaptcha.getValidateCodeId());
        request.add("validateCode", duplicateCaptcha.getValidateCode());
        send("/auth/registerUser", request).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号已经存在"));
    }
}
