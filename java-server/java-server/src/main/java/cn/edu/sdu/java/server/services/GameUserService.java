package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.*;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.*;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.DateTimeTool;
import cn.edu.sdu.java.server.util.FieldLimits;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 管理账户资料和角色，删除时同时清理账户关联 */
@Service
public class GameUserService {
    private final UserRepository userRepository;
    private final UserTypeRepository userTypeRepository;
    private final GameRecordRepository gameRecordRepository;
    private final PasswordEncoder encoder;
    private final BlockcoinWalletRepository walletRepository;
    private final MessageService messages;

    /** 保存账户、角色和记录仓库，密码操作复用同一编码器 */
    public GameUserService(UserRepository userRepository, UserTypeRepository userTypeRepository,
                           GameRecordRepository gameRecordRepository, PasswordEncoder encoder,
                           BlockcoinWalletRepository walletRepository, MessageService messages) {
        this.userRepository = userRepository;
        this.userTypeRepository = userTypeRepository;
        this.gameRecordRepository = gameRecordRepository;
        this.encoder = encoder;
        this.walletRepository = walletRepository;
        this.messages = messages;
    }

    /** 比较资料字段后只记录发生变化的字段名，通知正文保留联系方式隐私 */
    private List<String> profileChanges(User user, DataRequest request) {
        List<String> changes = new ArrayList<String>();
        if (!Objects.equals(user.getName(), request.getString("name"))) {
            changes.add("昵称");
        }
        if (!Objects.equals(user.getEmail(), request.getString("email"))) {
            changes.add("邮箱");
        }
        if (!Objects.equals(user.getPhone(), request.getString("phone"))) {
            changes.add("电话");
        }
        if (!Objects.equals(user.getSignature(), request.getString("signature"))) {
            changes.add("个性签名");
        }
        return changes;
    }

    /** 一次资料保存只发送一条通知，重复保存相同字段时保持消息列表原值 */
    private void notifyChanges(User user, List<String> changes) {
        if (changes.isEmpty()) {
            return;
        }
        String content = "已更新：" + String.join("、", changes);
        if (changes.contains("账户身份")) {
            String role = "游戏用户";
            if ("ROLE_ADMIN".equals(user.getUserType().getName())) {
                role = "管理员";
            }
            content = content + "\n当前身份：" + role;
        }
        messages.notifyAccount("profile:" + UUID.randomUUID(), user.getUserId(), "账户资料变更", content);
    }

    /**
     * 将账户对象整理成前端需要的资料对象
     * @param user 要转换的账户
     * @return 包含账号、昵称、联系方式和角色的 Map
     */
    public Map<String, Object> getAccountInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getUserId());
        info.put("username", user.getUserName());
        info.put("name", user.getName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("role", user.getUserType().getName());
        info.put("signature", user.getSignature());
        info.put("createTime", user.getCreateTime());
        info.put("lastLoginTime", user.getLastLoginTime());
        info.put("loginCount", user.getLoginCount());
        return info;
    }

    /** 查询账户资料及余额、游戏和记录数量，读取列表时保持钱包原值 */
    @Transactional(readOnly = true)
    public DataResponse getGameUserList(DataRequest request) {
        String search = request.getString("search");
        if (search == null) {
            search = "";
        }
        List<Map<String, Object>> accounts = new ArrayList<>();
        Map<Integer, Long> balances = new HashMap<Integer, Long>();
        for (BlockcoinWallet wallet : walletRepository.findAll()) {
            balances.put(wallet.getUserId(), wallet.getBalance());
        }
        for (User user : userRepository.findAll()) {
            String name = user.getName();
            if (name == null) {
                name = "";
            }
            if (user.getUserName().contains(search) || name.contains(search)) {
                Map<String, Object> info = getAccountInfo(user);
                info.put("balance", balances.get(user.getUserId()));
                info.put("gameCount", user.getGames().size());
                info.put("favoriteCount", user.getFavorites().size());
                info.put("recordCount", gameRecordRepository.countByUserUserId(user.getUserId()));
                accounts.add(info);
            }
        }
        return CommonMethod.getReturnData(accounts);
    }

    /**
     * 查询当前登录账户的个人资料
     * @return 当前账户资料
     */
    public DataResponse getMyProfile() {
        Integer userId = CommonMethod.getUserId();
        User user = userRepository.findById(userId).orElseThrow();
        return CommonMethod.getReturnData(getAccountInfo(user));
    }

    /**
     * 创建一个新的管理员或游戏用户账户，并保存密码哈希
     * @param request data 中包含账号、密码、昵称、邮箱、签名和角色
     * @return 创建成功或失败的响应
     */
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
        if (username.length() > FieldLimits.USERNAME || password.length() > FieldLimits.PASSWORD) {
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
        messages.notifyAccount("welcome:" + user.getUserId(), user.getUserId(),
            "账户通知", "欢迎使用 4398 游戏平台");
        return CommonMethod.getReturnMessageOK();
    }

    // 保存账户资料，普通用户只能修改自己的资料
    /**
     * 保存账户的个人资料，管理员可以指定 userId 修改其他账户
     * @param request data 中包含 userId、昵称、邮箱、电话和签名
     * @return 保存成功或失败的响应
     */
    @Transactional
    public DataResponse saveProfile(DataRequest request) {
        Integer userId = CommonMethod.getUserId();
        boolean admin = "ROLE_ADMIN".equals(CommonMethod.getRoleName());
        if (admin && request.getInteger("userId") != null) {
            userId = request.getInteger("userId");
        }
        User user = userRepository.findById(userId).orElseThrow();
        List<String> changes = profileChanges(user, request);
        user.setName(request.getString("name"));
        user.setEmail(request.getString("email"));
        user.setPhone(request.getString("phone"));
        user.setSignature(request.getString("signature"));
        userRepository.save(user);
        notifyChanges(user, changes);
        return CommonMethod.getReturnMessageOK();
    }

    // 删除账号时保护系统 admin 和当前账号
    /**
     * 删除账号，并清理账户关联的游戏库、收藏和游戏记录
     * @param request data 中包含 userId
     * @return 删除成功或失败的响应
     */
    @Transactional
    public DataResponse deleteAccount(DataRequest request) {
        Integer userId = request.getInteger("userId");
        if (userId == null || userId.equals(CommonMethod.getUserId())) {
            return CommonMethod.getReturnMessageError("不能删除当前账号");
        }
        User user = userRepository.findById(userId).orElseThrow();
        if ("admin".equalsIgnoreCase(user.getUserName())) {
            return CommonMethod.getReturnMessageError("系统管理员 admin 不可删除");
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

    /** 管理员保存资料与角色，系统账号和当前账号保持管理员身份 */
    @Transactional
    public DataResponse saveAccount(DataRequest request) {
        Integer id = request.getInteger("userId");
        if (id == null) {
            return CommonMethod.getReturnMessageError("请选择要修改的账号");
        }
        Optional<User> found = userRepository.findById(id);
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("账号不存在");
        }
        String role = request.getString("role");
        if (!"ROLE_ADMIN".equals(role) && !"ROLE_GAME_USER".equals(role)) {
            return CommonMethod.getReturnMessageError("请选择有效的账户身份");
        }
        User user = found.get();
        if (("admin".equalsIgnoreCase(user.getUserName()) || id.equals(CommonMethod.getUserId()))
                && !"ROLE_ADMIN".equals(role)) {
            return CommonMethod.getReturnMessageError("不能取消系统 admin 或当前账号的管理员身份");
        }
        List<String> changes = profileChanges(user, request);
        if (!user.getUserType().getName().equals(role)) {
            changes.add("账户身份");
        }
        user.setName(request.getString("name"));
        user.setEmail(request.getString("email"));
        user.setPhone(request.getString("phone"));
        user.setSignature(request.getString("signature"));
        user.setUserType(userTypeRepository.findByName(role));
        userRepository.save(user);
        notifyChanges(user, changes);
        return CommonMethod.getReturnMessageOK();
    }
}
