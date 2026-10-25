package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class GameUserService {
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final GameRecordRepository gameRecordRepository;
    private final PasswordEncoder encoder;

    public GameUserService(UserRepository userRepository, UserTypeRepository userTypeRepository,
                           GameRecordRepository gameRecordRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.gameRecordRepository = gameRecordRepository;
        this.encoder = encoder;
    }

    public Map<String, Object> getAccountInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getUserId());
        info.put("username", user.getUserName());
        info.put("name", user.getName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("role", user.getUserType().getName());
        info.put("signature", user.getSignature());
        return info;
    }

    // 查询游戏用户和管理员的资料，返回账户列表。
    public DataResponse getGameUserList(DataRequest request) {
        String search = request.getString("search");
        if (search == null) {
            search = "";
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            String name = user.getName();
            if (name == null) {
                name = "";
            }
            if (user.getUserName().contains(search) || name.contains(search)) {
                accounts.add(getAccountInfo(user));
            }
        }
        return CommonMethod.getReturnData(accounts);
    }

    public DataResponse getMyProfile() {
        Integer userId = CommonMethod.getUserId();
        User user = userRepository.findById(userId).orElseThrow();
        return CommonMethod.getReturnData(getAccountInfo(user));
    }

    @Transactional
    public DataResponse createAccount(DataRequest request) {
        String username = request.getString("username");
        String password = request.getString("password");
        String name = request.getString("name");
        if (name == null) {
            name = request.getString("perName");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return CommonMethod.getReturnMessageError("账号和密码不能为空");
        }
        if (username.length() > 20 || password.length() > 60) {
            return CommonMethod.getReturnMessageError("账号或密码过长");
        }
        if (userRepository.existsByUserName(username)) {
            return CommonMethod.getReturnMessageError("账号已经存在");
        }
        String role = request.getString("role");
        if (!"ROLE_ADMIN".equals(role) && !"ROLE_GAME_USER".equals(role)) {
            return CommonMethod.getReturnMessageError("请选择游戏用户或管理员");
        }
        User user = new User();
        user.setName(name);
        user.setEmail(request.getString("email"));
        user.setPhone(request.getString("phone"));
        user.setSignature(request.getString("signature"));
        user.setUserName(username);
        user.setPassword(encoder.encode(password));
        user.setUserType(userTypeRepository.findByName(role));
        user.setCreateTime(DateTimeTool.parseDateTime(new Date()));
        user.setLoginCount(0);
        userRepository.save(user);
        return CommonMethod.getReturnMessageOK();
    }

    // 保存账户资料，普通用户只能修改自己的资料。
    @Transactional
    public DataResponse saveProfile(DataRequest request) {
        Integer userId = CommonMethod.getUserId();
        boolean admin = "ROLE_ADMIN".equals(CommonMethod.getRoleName());
        if (admin && request.getInteger("userId") != null) {
            userId = request.getInteger("userId");
        }
        User user = userRepository.findById(userId).orElseThrow();
        user.setName(request.getString("name"));
        user.setEmail(request.getString("email"));
        user.setPhone(request.getString("phone"));
        user.setSignature(request.getString("signature"));
        userRepository.save(user);
        return CommonMethod.getReturnMessageOK();
    }

    // 删除游戏用户，管理员账号和当前账号不能在这里删除。
    @Transactional
    public DataResponse deleteAccount(DataRequest request) {
        Integer userId = request.getInteger("userId");
        if (userId == null || userId.equals(CommonMethod.getUserId())) {
            return CommonMethod.getReturnMessageError("不能删除当前账号");
        }
        User user = userRepository.findById(userId).orElseThrow();
        if ("ROLE_ADMIN".equals(user.getUserType().getName())) {
            return CommonMethod.getReturnMessageError("不能删除管理员账号");
        }
        user.getGames().clear();
        user.getFavorites().clear();
        userRepository.saveAndFlush(user);
        gameRecordRepository.deleteByUserUserId(userId);
        gameRecordRepository.flush();
        userRepository.delete(user);
        userRepository.flush();
        return CommonMethod.getReturnMessageOK();
    }
}
