package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import cn.edu.sdu.java.server.util.LoginControlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GameMigrationTests {
    @Autowired
    private MockMvc client;
    @Autowired
    private GameRepository games;
    @Autowired
    private UserRepository users;
    @Autowired
    private ObjectMapper mapper;

    private MockHttpServletRequestBuilder request(String path, String body, Integer accountId) {
        User account = users.findById(accountId).orElseThrow();
        UserDetailsImpl details = UserDetailsImpl.build(account);
        MockHttpServletRequestBuilder request = post(path);
        request.with(user(details));
        request.contentType(MediaType.APPLICATION_JSON);
        request.content(body);
        return request;
    }

    private Game createGame() {
        Game game = new Game();
        game.setNum("migration-check");
        game.setName("接口验证游戏");
        return games.saveAndFlush(game);
    }

    @Test
    void onlyAdministratorCanManageAccountsAndGames() throws Exception {
        client.perform(request("/api/gameUser/list", "{\"data\":{}}", 1))
                .andExpect(MockMvcResultMatchers.status().isOk());
        client.perform(request("/api/gameUser/list", "{\"data\":{}}", 2))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        client.perform(request("/api/game/save", "{\"data\":{\"num\":\"check\",\"name\":\"check\"}}", 2))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void libraryFavoritesAndRecordsBelongToCurrentAccount() throws Exception {
        Game game = createGame();
        String gameId = game.getGameId().toString();
        String collection = "{\"data\":{\"gameId\":" + gameId + ",\"mode\":\"library\",\"added\":true}}";
        client.perform(request("/api/game/collection", collection, 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        client.perform(request("/api/game/list", "{\"data\":{\"mode\":\"library\"}}", 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].gameId").value(game.getGameId()));
        client.perform(request("/api/game/list", "{\"data\":{\"mode\":\"library\"}}", 3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty());
        String favorite = "{\"data\":{\"gameId\":" + gameId + ",\"mode\":\"favorites\",\"added\":true}}";
        client.perform(request("/api/game/collection", favorite, 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        client.perform(request("/api/game/list", "{\"data\":{\"mode\":\"favorites\"}}", 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].gameId").value(game.getGameId()));
        String record = "{\"data\":{\"gameId\":" + gameId + ",\"score\":120,\"durationSeconds\":30}}";
        client.perform(request("/api/game/recordSave", record, 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        client.perform(request("/api/game/records", "{\"data\":{}}", 2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].score").value(120));
        client.perform(request("/api/game/records", "{\"data\":{}}", 3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty());
        client.perform(request("/api/game/delete", "{\"data\":{\"gameId\":" + gameId + "}}", 1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(1));
    }

    @Test
    void registrationAlwaysCreatesGameUser() throws Exception {
        String body = "{\"data\":{\"username\":\"migration_user\",\"password\":\"Test123!\",\"perName\":\"验证账户\",\"role\":\"ADMIN\"}}";
        Map<String, Object> captcha = LoginControlUtil.getInstance().getValidateCodeDataMap();
        Integer captchaId = (Integer) captcha.get("validateCodeId");
        cn.edu.sdu.java.server.payload.request.DataRequest registration = mapper.readValue(
                body, cn.edu.sdu.java.server.payload.request.DataRequest.class);
        registration.add("validateCodeId", captchaId);
        registration.add("validateCode", LoginControlUtil.getInstance().getValidateCode(captchaId));
        body = mapper.writeValueAsString(registration);
        client.perform(post("/auth/registerUser").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        User account = users.findByUserName("migration_user").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("ROLE_GAME_USER", account.getUserType().getName());
    }

    @Test
    void administratorCanCreateEditAndDeleteGameAccount() throws Exception {
        String createBody = "{\"data\":{\"username\":\"migration_user\",\"password\":\"Test123!\",\"name\":\"验证账户\",\"role\":\"ROLE_GAME_USER\"}}";
        client.perform(request("/api/gameUser/create", createBody, 1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        User account = users.findByUserName("migration_user").orElseThrow();
        String userId = account.getUserId().toString();
        String saveBody = "{\"data\":{\"userId\":" + userId + ",\"name\":\"修改后的昵称\",\"email\":\"check@example.com\"}}";
        client.perform(request("/api/gameUser/save", saveBody, 1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        String deleteBody = "{\"data\":{\"userId\":" + userId + "}}";
        client.perform(request("/api/gameUser/delete", deleteBody, 1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(0));
        org.junit.jupiter.api.Assertions.assertFalse(users.existsByUserName("migration_user"));
    }
}
