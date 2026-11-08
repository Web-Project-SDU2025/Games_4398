package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.MessageReceipt;
import cn.edu.sdu.java.server.models.FeedbackResolution;
import cn.edu.sdu.java.server.models.SiteMessage;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.MessageReceiptRepository;
import cn.edu.sdu.java.server.repositorys.FeedbackResolutionRepository;
import cn.edu.sdu.java.server.repositorys.SiteMessageRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.AdminQueryRules;
import cn.edu.sdu.java.server.util.CommonMethod;
import cn.edu.sdu.java.server.util.MessageRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息中心服务
 *
 * 处理四种类型的消息：系统通知、全站公告、问题反馈和管理员回复
 * 每条消息都有全局唯一的正文，但每个账户的已读状态是单独存储的
 * 比如张三读过的公告，李四那边还是未读状态
 */
@Service
public class MessageService {
    private final SiteMessageRepository messages;
    private final MessageReceiptRepository receipts;
    private final FeedbackResolutionRepository feedbackResolutions;
    private final UserRepository users;

    /**
     * 依赖注入
     * 消息正文和个人已读回执分开存储，用账户锁防止并发写入时产生重复记录
     */
    public MessageService(SiteMessageRepository messages, MessageReceiptRepository receipts,
            FeedbackResolutionRepository feedbackResolutions, UserRepository users) {
        this.messages = messages;
        this.receipts = receipts;
        this.feedbackResolutions = feedbackResolutions;
        this.users = users;
    }

    /**
     * 从账户ID判断管理员身份
     * 优先从数据库读取最新的角色信息，避免用户被降级后还能执行管理员操作
     */
    private boolean isAdministrator(Integer userId) {
        Optional<User> found = users.findById(userId);
        if (found.isEmpty()) {
            return false;
        }
        return isAdministrator(found.get());
    }

    /**
     * 从账户对象判断管理员身份
     * 如果角色数据缺失就当普通用户处理，宁可限制权限也不能误开放
     */
    private boolean isAdministrator(User user) {
        if (user.getUserType() == null) {
            return false;
        }
        return "ROLE_ADMIN".equals(user.getUserType().getName());
    }

    /**
     * 判断用户是否有权限阅读某条消息
     * 账户通知只能自己看，公告所有人都能看，反馈提交者和管理员可以看
     */
    private boolean canReadMessage(User reader, SiteMessage message) {
        Integer recipientId = message.getRecipientId();
        if (recipientId != null) {
            return recipientId.equals(reader.getUserId());
        }
        if ("announcement".equals(message.getKind())) {
            return true;
        }
        if ("feedback".equals(message.getKind())) {
            if (isAdministrator(reader)) {
                return true;
            }
            Integer senderId = message.getSenderId();
            return senderId != null && senderId.equals(reader.getUserId());
        }
        return false;
    }

    /**
     * 校验操作编号格式
     * 必须是标准的UUID格式，防止用户随便传个字符串过来
     */
    private boolean validRequestId(String requestId) {
        if (requestId == null) {
            return false;
        }
        try {
            return UUID.fromString(requestId).toString().equals(requestId);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /**
     * 校验消息文本
     * 标题和内容都不能为空，长度也要在合理范围内
     */
    private boolean validMessageText(String title, String content) {
        if (title == null || title.isBlank()) {
            return false;
        }
        if (title.trim().length() > MessageRules.TITLE_LIMIT) {
            return false;
        }
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.trim().length() <= MessageRules.CONTENT_LIMIT;
    }

    /**
     * 发送账户通知
     * 业务服务调用这个方法给用户发通知，比如购买成功、字块币到账等
     * 和业务操作在同一个事务里，业务回滚的话通知也不会发
     */
    @Transactional
    public void notifyAccount(String id, int userId, String title, String content) {
        SiteMessage message = new SiteMessage();
        message.setMessageId(id);
        message.setRecipientId(userId);
        message.setSenderName("系统通知");
        message.setKind("account");
        message.setTitle(title);
        message.setContent(content);
        message.setCreatedAt(Instant.now());
        messages.save(message);
    }

    /**
     * 解析分页参数
     * 只接受纯整数，小数和超大数字会抛异常让调用方返回参数错误
     */
    private int pageNumber(DataRequest request, String field, int fallback, int limit) {
        String text = request.getString(field);
        if (text == null) {
            return fallback;
        }
        int value = Integer.parseInt(text);
        if (value < 1 || value > limit) {
            throw new IllegalArgumentException("消息分页参数不正确");
        }
        return value;
    }

    /**
     * 组装消息数据
     * 把消息正文、已读状态、反馈处理结果打包成前端需要的格式
     */
    private Map<String, Object> messageInfo(SiteMessage message, boolean read,
            FeedbackResolution resolution) {
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("messageId", message.getMessageId());
        row.put("kind", message.getKind());
        row.put("senderId", message.getSenderId());
        row.put("title", message.getTitle());
        row.put("content", message.getContent());
        row.put("senderName", message.getSenderName());
        row.put("createdAt", message.getCreatedAt());
        row.put("read", read);
        if ("feedback".equals(message.getKind())) {
            String decision = MessageRules.FEEDBACK_PENDING;
            String reply = "";
            Instant updatedAt = null;
            if (resolution != null) {
                decision = resolution.getDecision();
                reply = resolution.getReply();
                updatedAt = resolution.getUpdatedAt();
            }
            row.put("feedbackDecision", decision);
            row.put("adminReply", reply);
            row.put("replyUpdatedAt", updatedAt);
        }
        return row;
    }

    /**
     * 获取消息列表
     * 支持按类型筛选、只看未读、分页等功能
     * 会同时返回未读总数，方便顶栏显示红点提示
     */
    @Transactional(readOnly = true)
    public DataResponse list(DataRequest request) {
        try {
            int userId = CommonMethod.getUserId();
            int number = pageNumber(request, "page", 1, MessageRules.MAX_PAGE);
            int size = pageNumber(request, "pageSize", AdminQueryRules.DEFAULT_PAGE_SIZE,
                AdminQueryRules.MAX_PAGE_SIZE);
            String kind = request.getString("kind");
            if (kind == null) {
                kind = "all";
            }
            if (!List.of("all", "account", "announcement", "feedback").contains(kind)) {
                return CommonMethod.getReturnMessageError("消息类型不正确");
            }
            boolean administrator = isAdministrator(userId);
            Page<SiteMessage> page = messages.inbox(userId, administrator, kind,
                request.getBoolean("unreadOnly"), PageRequest.of(number - 1, size));
            List<String> ids = new ArrayList<String>();
            for (SiteMessage message : page.getContent()) {
                ids.add(message.getMessageId());
            }
            Set<String> read = new HashSet<String>();
            Map<String, FeedbackResolution> resolutions = new HashMap<String, FeedbackResolution>();
            if (!ids.isEmpty()) {
                for (MessageReceipt receipt : receipts.findByUserIdAndMessageIdIn(userId, ids)) {
                    read.add(receipt.getMessageId());
                }
                for (FeedbackResolution resolution
                        : feedbackResolutions.findByMessageIdIn(ids)) {
                    resolutions.put(resolution.getMessageId(), resolution);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (SiteMessage message : page.getContent()) {
                String id = message.getMessageId();
                boolean alreadyRead = read.contains(id);
                FeedbackResolution resolution = resolutions.get(id);
                rows.add(messageInfo(message, alreadyRead, resolution));
            }
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("messages", rows);
            result.put("total", page.getTotalElements());
            result.put("page", number);
            result.put("pageSize", size);
            result.put("unreadCount", messages.unreadCount(userId, administrator));
            return CommonMethod.getReturnData(result);
        } catch (IllegalArgumentException error) {
            return CommonMethod.getReturnMessageError("消息查询参数不正确");
        }
    }

    /**
     * 查询未读消息数量
     * 顶栏会定时调这个接口刷新红点，页面隐藏时前端会暂停轮询节省资源
     */
    @Transactional(readOnly = true)
    public DataResponse unread() {
        Integer userId = CommonMethod.getUserId();
        boolean administrator = isAdministrator(userId);
        return CommonMethod.getReturnData(messages.unreadCount(userId, administrator));
    }

    /**
     * 记录一条已读回执
     * 用账户ID和消息ID拼接成唯一键，重复调用会保持原来的已读时间
     */
    private void receipt(int userId, String messageId, Instant now) {
        MessageReceipt receipt = new MessageReceipt();
        receipt.setReceiptId(userId + ":" + messageId);
        receipt.setUserId(userId);
        receipt.setMessageId(messageId);
        receipt.setReadAt(now);
        receipts.save(receipt);
    }

    /**
     * 标记单条消息为已读
     * 会检查消息是否存在、当前用户是否有权限查看
     * 已读过的消息重复调用也没关系，保持已读状态不变
     */
    @Transactional
    public DataResponse read(DataRequest request) {
        int userId = CommonMethod.getUserId();
        Optional<User> foundUser = users.lockAccount(userId);
        if (foundUser.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        String id = request.getString("messageId");
        if (id == null || id.isBlank() || id.length() > MessageRules.ID_LIMIT) {
            return CommonMethod.getReturnMessageError("消息编号不正确");
        }
        Optional<SiteMessage> found = messages.findById(id);
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("消息不存在");
        }
        SiteMessage message = found.get();
        if (!canReadMessage(foundUser.get(), message)) {
            return CommonMethod.getReturnMessageError("消息不存在");
        }
        if (!receipts.existsById(userId + ":" + id)) {
            receipt(userId, id, Instant.now());
        }
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 一键全部已读
     * 会分批处理避免一次更新太多记录导致数据库超时
     * 以请求开始时间为界，这个时间之后新来的消息不受影响
     */
    @Transactional
    public DataResponse readAll() {
        int userId = CommonMethod.getUserId();
        Optional<User> found = users.lockAccount(userId);
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        boolean administrator = isAdministrator(found.get());
        Instant cutoff = Instant.now();
        PageRequest page = PageRequest.of(0, MessageRules.RECEIPT_BATCH_SIZE);
        List<SiteMessage> batch = messages.unreadBatch(userId, administrator, cutoff, page);
        while (!batch.isEmpty()) {
            for (SiteMessage message : batch) {
                receipt(userId, message.getMessageId(), cutoff);
            }
            receipts.flush();
            batch = messages.unreadBatch(userId, administrator, cutoff, page);
        }
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 公告和反馈的通用保存逻辑
     * 填充消息正文的公共字段，具体的权限检查和重复处理由各自的接口负责
     */
    private void saveAuthoredMessage(String id, User sender, String kind,
            String title, String content, Instant now) {
        SiteMessage message = new SiteMessage();
        message.setMessageId(id);
        message.setSenderId(sender.getUserId());
        message.setSenderName(sender.getUserName());
        message.setKind(kind);
        message.setTitle(title.trim());
        message.setContent(content.trim());
        message.setCreatedAt(now);
        messages.save(message);
    }

    /**
     * 发布全站公告
     * 只有管理员能发，会检查发布频率防止刷屏
     * 操作编号保证网络抖动时重复点击不会发两遍
     */
    @Transactional
    public DataResponse publish(DataRequest request) {
        if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())) {
            return CommonMethod.getReturnMessageError("仅管理员可用");
        }
        String requestId = request.getString("requestId");
        if (!validRequestId(requestId)) {
            return CommonMethod.getReturnMessageError("公告操作编号不正确");
        }
        String title = request.getString("title");
        String content = request.getString("content");
        if (!validMessageText(title, content)) {
            return CommonMethod.getReturnMessageError("请填写有效的公告标题与内容");
        }
        Optional<User> found = users.lockAccount(CommonMethod.getUserId());
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        User actor = found.get();
        if (!isAdministrator(actor)) {
            return CommonMethod.getReturnMessageError("管理员身份已变更");
        }
        String id = "announcement:" + actor.getUserId() + ":" + requestId;
        Optional<SiteMessage> previous = messages.findById(id);
        if (previous.isPresent()) {
            SiteMessage message = previous.get();
            if (!message.getTitle().equals(title.trim()) || !message.getContent().equals(content.trim())) {
                return CommonMethod.getReturnMessageError("操作编号已用于其他公告");
            }
            return CommonMethod.getReturnData(id);
        }
        Instant now = Instant.now();
        SiteMessage latest = messages.findFirstBySenderIdAndKindOrderByCreatedAtDesc(
            actor.getUserId(), "announcement");
        if (latest != null && now.isBefore(
                latest.getCreatedAt().plusSeconds(MessageRules.PUBLISH_INTERVAL_SECONDS))) {
            return CommonMethod.getReturnMessageError("请稍后再发布公告");
        }
        saveAuthoredMessage(id, actor, "announcement", title, content, now);
        return CommonMethod.getReturnData(id, "公告已发布");
    }

    /**
     * 提交问题反馈
     * 所有用户都可以向管理员提交反馈，只有管理员和提交者能看到这条反馈
     * 提交后会自动标记为已读，这样提交者在消息列表里就能看到它
     */
    @Transactional
    public DataResponse feedback(DataRequest request) {
        int userId = CommonMethod.getUserId();
        String requestId = request.getString("requestId");
        if (!validRequestId(requestId)) {
            return CommonMethod.getReturnMessageError("反馈操作编号不正确");
        }
        String title = request.getString("title");
        String content = request.getString("content");
        if (!validMessageText(title, content)) {
            return CommonMethod.getReturnMessageError("请填写有效的反馈标题与内容");
        }
        Optional<User> found = users.lockAccount(userId);
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        User sender = found.get();
        String id = "feedback:" + sender.getUserId() + ":" + requestId;
        Optional<SiteMessage> previous = messages.findById(id);
        if (previous.isPresent()) {
            SiteMessage message = previous.get();
            if (!"feedback".equals(message.getKind())
                    || !sender.getUserId().equals(message.getSenderId())
                    || !message.getTitle().equals(title.trim())
                    || !message.getContent().equals(content.trim())) {
                return CommonMethod.getReturnMessageError("操作编号已用于其他反馈");
            }
            return CommonMethod.getReturnData(id, "反馈已发送");
        }
        List<User> administrators = users.findByUserTypeName("ROLE_ADMIN");
        if (administrators.isEmpty()) {
            return CommonMethod.getReturnMessageError("当前没有可接收反馈的管理员");
        }
        Instant now = Instant.now();
        saveAuthoredMessage(id, sender, "feedback", title, content, now);
        receipt(sender.getUserId(), id, now);
        return CommonMethod.getReturnData(id, "反馈已发送");
    }

    /**
     * 编辑消息
     * 管理员可以编辑公告，提交者可以编辑自己的反馈
     * 编辑后会清空所有人的已读状态，让大家重新看到更新后的内容
     * 反馈编辑后会清除管理员的处理状态，因为内容变了需要重新审核
     */
    @Transactional
    public DataResponse edit(DataRequest request) {
        int userId = CommonMethod.getUserId();
        Optional<User> foundUser = users.lockAccount(userId);
        if (foundUser.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        User editor = foundUser.get();
        String id = request.getString("messageId");
        if (id == null || id.isBlank() || id.length() > MessageRules.ID_LIMIT) {
            return CommonMethod.getReturnMessageError("消息编号不正确");
        }
        Optional<SiteMessage> foundMessage = messages.findById(id);
        if (foundMessage.isEmpty()) {
            return CommonMethod.getReturnMessageError("消息不存在");
        }
        SiteMessage message = foundMessage.get();
        if ("announcement".equals(message.getKind())) {
            if (!isAdministrator(editor)) {
                return CommonMethod.getReturnMessageError("仅管理员可编辑公告");
            }
        } else if ("feedback".equals(message.getKind())) {
            Integer senderId = message.getSenderId();
            if (senderId == null || !senderId.equals(editor.getUserId())) {
                return CommonMethod.getReturnMessageError("只能编辑本人提交的反馈");
            }
        } else {
            return CommonMethod.getReturnMessageError("此类消息不能编辑");
        }
        String title = request.getString("title");
        String content = request.getString("content");
        if (!validMessageText(title, content)) {
            return CommonMethod.getReturnMessageError("请填写有效的消息标题与内容");
        }
        String updatedTitle = title.trim();
        String updatedContent = content.trim();
        if (message.getTitle().equals(updatedTitle)
                && message.getContent().equals(updatedContent)) {
            return CommonMethod.getReturnData(id, "消息内容未改变");
        }
        message.setTitle(updatedTitle);
        message.setContent(updatedContent);
        messages.save(message);
        if ("feedback".equals(message.getKind())) {
            Optional<FeedbackResolution> resolution = feedbackResolutions.findById(id);
            if (resolution.isPresent()) {
                feedbackResolutions.delete(resolution.get());
            }
        }
        receipts.deleteByMessageId(id);
        receipt(editor.getUserId(), id, Instant.now());
        return CommonMethod.getReturnData(id, "消息已修改");
    }

    /**
     * 管理员回复问题反馈
     * 可以设置处理状态（将会修复、不会修复、已解决）并填写回复内容
     * 提交者会收到未读提醒，但管理员自己提交的反馈不会给自己发未读
     */
    @Transactional
    public DataResponse respondFeedback(DataRequest request) {
        int administratorId = CommonMethod.getUserId();
        Optional<User> foundAdministrator = users.lockAccount(administratorId);
        if (foundAdministrator.isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        User administrator = foundAdministrator.get();
        if (!isAdministrator(administrator)) {
            return CommonMethod.getReturnMessageError("仅管理员可回复问题反馈");
        }
        String id = request.getString("messageId");
        if (id == null || id.isBlank() || id.length() > MessageRules.ID_LIMIT) {
            return CommonMethod.getReturnMessageError("反馈编号不正确");
        }
        String decision = request.getString("decision");
        if (!MessageRules.FEEDBACK_WILL_FIX.equals(decision)
                && !MessageRules.FEEDBACK_WILL_NOT_FIX.equals(decision)
                && !MessageRules.FEEDBACK_RESOLVED.equals(decision)) {
            return CommonMethod.getReturnMessageError("请选择反馈处理状态");
        }
        String reply = request.getString("reply");
        if (reply == null || reply.isBlank()
                || reply.trim().length() > MessageRules.CONTENT_LIMIT) {
            return CommonMethod.getReturnMessageError("请填写有效的回复内容");
        }
        Optional<SiteMessage> foundMessage = messages.findById(id);
        if (foundMessage.isEmpty() || !"feedback".equals(foundMessage.get().getKind())) {
            return CommonMethod.getReturnMessageError("问题反馈不存在");
        }
        SiteMessage feedback = foundMessage.get();
        if (feedback.getSenderId() == null) {
            return CommonMethod.getReturnMessageError("反馈提交账户不存在");
        }
        String updatedReply = reply.trim();
        Optional<FeedbackResolution> foundResolution = feedbackResolutions.findById(id);
        if (foundResolution.isPresent()) {
            FeedbackResolution current = foundResolution.get();
            if (current.getDecision().equals(decision)
                    && current.getReply().equals(updatedReply)) {
                return feedbackResult(id, current);
            }
        }
        Instant now = Instant.now();
        FeedbackResolution resolution;
        if (foundResolution.isPresent()) {
            resolution = foundResolution.get();
        } else {
            resolution = new FeedbackResolution();
            resolution.setMessageId(id);
        }
        resolution.setDecision(decision);
        resolution.setAdministratorId(administrator.getUserId());
        resolution.setReply(updatedReply);
        resolution.setUpdatedAt(now);
        feedbackResolutions.save(resolution);
        receipts.deleteByUserIdAndMessageId(feedback.getSenderId(), id);
        if (feedback.getSenderId().equals(administrator.getUserId())) {
            receipt(administrator.getUserId(), id, now);
        }
        return feedbackResult(id, resolution);
    }

    /**
     * 组装反馈处理结果
     * 给前端返回管理员的回复内容和处理状态，用于立即更新页面
     */
    private DataResponse feedbackResult(String id, FeedbackResolution resolution) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("messageId", id);
        result.put("decision", resolution.getDecision());
        result.put("reply", resolution.getReply());
        result.put("updatedAt", resolution.getUpdatedAt());
        return CommonMethod.getReturnData(result, "反馈处理已保存");
    }
}
