package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.models.Game;
import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.GameRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.GameUserService;
import cn.edu.sdu.java.server.services.BaseService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserMergeTests {
    @Autowired
    private UserRepository users;
    @Autowired
    private GameRepository games;
    @Autowired
    private GameRecordRepository records;
    @Autowired
    private GameUserService service;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate database;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private BaseService baseService;

    private void signIn(Integer userId) {
        User user = users.findById(userId).orElseThrow();
        UserDetailsImpl details = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User createAccount() {
        DataRequest request = new DataRequest();
        request.add("username", "merge_check");
        request.add("password", "Test123!");
        request.add("name", "合并验证");
        request.add("email", "merge@example.com");
        request.add("signature", "验证签名");
        request.add("role", "ROLE_GAME_USER");
        assertEquals(0, service.createAccount(request).getCode());
        return users.findByUserName("merge_check").orElseThrow();
    }

    @Test
    void newAccountStoresAllProfileFieldsInOneTable() {
        User user = createAccount();
        Integer userId = user.getUserId();
        byte[] photo = new byte[] {1, 2, 3, 4};
        user.setPhoto(photo);
        user.setGender("1");
        user.setBirthday("2000-01-01");
        user.setAddress("验证地址");
        user.setIntroduce("验证简介");
        users.saveAndFlush(user);
        entityManager.clear();
        User saved = users.findById(userId).orElseThrow();
        assertEquals("合并验证", saved.getName());
        assertEquals("merge@example.com", saved.getEmail());
        assertEquals("验证签名", saved.getSignature());
        assertEquals("1", saved.getGender());
        assertEquals("2000-01-01", saved.getBirthday());
        assertEquals("验证地址", saved.getAddress());
        assertEquals("验证简介", saved.getIntroduce());
        assertArrayEquals(photo, saved.getPhoto());
        assertTrue(encoder.matches("Test123!", saved.getPassword()));
        Integer oldTables = database.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('person','game_user')",
                Integer.class);
        assertEquals(0, oldTables);
    }

    @Test
    void ordinaryUserCannotEditAnotherAccount() {
        signIn(2);
        try {
            User administrator = users.findById(1).orElseThrow();
            String originalName = administrator.getName();
            DataRequest request = new DataRequest();
            request.add("userId", 1);
            request.add("name", "自己的新昵称");
            request.add("email", "self@example.com");
            request.add("signature", "新的签名");
            assertEquals(0, service.saveProfile(request).getCode());
            users.flush();
            entityManager.clear();
            assertEquals(originalName, users.findById(1).orElseThrow().getName());
            User current = users.findById(2).orElseThrow();
            assertEquals("自己的新昵称", current.getName());
            assertEquals("新的签名", current.getSignature());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void deletingAccountRemovesItsRelationsAndKeepsGame() {
        User user = createAccount();
        Integer userId = user.getUserId();
        Game game = new Game();
        game.setNum("merge-game");
        game.setName("验证游戏");
        games.saveAndFlush(game);
        Integer gameId = game.getGameId();
        user.getGames().add(game);
        user.getFavorites().add(game);
        users.saveAndFlush(user);
        GameRecord record = new GameRecord();
        record.setUser(user);
        record.setGame(game);
        record.setScore(100);
        record.setDurationSeconds(30);
        record.setPlayedAt(LocalDateTime.now());
        records.saveAndFlush(record);
        entityManager.clear();
        User saved = users.findById(userId).orElseThrow();
        assertEquals(1, saved.getGames().size());
        assertEquals(1, saved.getFavorites().size());
        assertEquals(1, records.findByUserUserIdOrderByPlayedAtDesc(userId).size());
        signIn(1);
        try {
            DataRequest request = new DataRequest();
            request.add("userId", userId);
            assertEquals(0, service.deleteAccount(request).getCode());
            entityManager.flush();
            entityManager.clear();
            assertFalse(users.existsById(userId));
            assertTrue(games.existsById(gameId));
            assertTrue(records.findByUserUserIdOrderByPlayedAtDesc(userId).isEmpty());
            assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM user_game WHERE user_id=?", Integer.class, userId));
            assertEquals(0, database.queryForObject("SELECT COUNT(*) FROM game_favorite WHERE user_id=?", Integer.class, userId));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void passwordChangeAcceptsSimplePasswordsAndLimitsMaximumLength() {
        User account = createAccount();
        Integer userId = account.getUserId();
        signIn(userId);
        try {
            DataRequest request = new DataRequest();
            request.add("oldPassword", "Test123!");
            request.add("newPassword", "1");
            assertEquals(0, baseService.updatePassword(request).getCode());
            users.flush();
            entityManager.clear();
            assertTrue(encoder.matches("1", users.findById(userId).orElseThrow().getPassword()));

            request.add("oldPassword", "wrong");
            request.add("newPassword", "abc");
            assertEquals(1, baseService.updatePassword(request).getCode());

            request.add("oldPassword", "1");
            request.add("newPassword", "abcdefghijklmnopqrst");
            assertEquals(0, baseService.updatePassword(request).getCode());
            request.add("oldPassword", "abcdefghijklmnopqrst");
            request.add("newPassword", "abcdefghijklmnopqrstu");
            assertEquals(1, baseService.updatePassword(request).getCode());
            request.add("newPassword", "");
            assertEquals(1, baseService.updatePassword(request).getCode());
            users.flush();
            entityManager.clear();
            assertTrue(encoder.matches("abcdefghijklmnopqrst", users.findById(userId).orElseThrow().getPassword()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
