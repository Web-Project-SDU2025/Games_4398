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

/** 消息中心处理通知、公告、反馈与个人已读状态 */
@Service
public class MessageService {
    private final SiteMessageRepository messages;
    private final MessageReceiptRepository receipts;
    private final FeedbackResolutionRepository feedbackResolutions;
    private final UserRepository users;

    /** 消息正文和个人回执分别存储，账户锁用于串行处理重复写入 */
    public MessageService(SiteMessageRepository messages, MessageReceiptRepository receipts,
            FeedbackResolutionRepository feedbackResolutions, UserRepository users) {
        this.messages = messages;
        this.receipts = receipts;
        this.feedbackResolutions = feedbackResolutions;
        this.users = users;
    }

    /** 按数据库中的账户角色判断管理员身份 */
    private boolean isAdministrator(Integer userId) {
        Optional<User> found = users.findById(userId);
        if (found.isEmpty()) {
            return false;
        }
        return isAdministrator(found.get());
    }

    /** 角色资料缺失时按普通账户处理 */
    private boolean isAdministrator(User user) {
        if (user.getUserType() == null) {
            return false;
        }
        return "ROLE_ADMIN".equals(user.getUserType().getName());
    }

    /** 检查当前账户能否阅读指定消息 */
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

    /** 检查操作编号是否为标准 UUID */
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

    /** 标题和正文不能为空，长度按消息统一上限校验 */
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

    /** 业务调用方已核对接收账户，通知与原业务沿用同一事务提交 */
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

    /** 解析正整数分页参数，小数、溢出和无效文本都返回参数错误 */
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

    /** 列表只返回当前账户可见消息，未读状态按个人回执整理 */
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
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("messageId", message.getMessageId());
                row.put("kind", message.getKind());
                row.put("senderId", message.getSenderId());
                row.put("title", message.getTitle());
                row.put("content", message.getContent());
                row.put("senderName", message.getSenderName());
                row.put("createdAt", message.getCreatedAt());
                row.put("read", read.contains(message.getMessageId()));
                if ("feedback".equals(message.getKind())) {
                    FeedbackResolution resolution = resolutions.get(message.getMessageId());
                    String decision = MessageRules.FEEDBACK_PENDING;
                    String reply = "";
                    Instant replyUpdatedAt = null;
                    if (resolution != null) {
                        decision = resolution.getDecision();
                        reply = resolution.getReply();
                        replyUpdatedAt = resolution.getUpdatedAt();
                    }
                    row.put("feedbackDecision", decision);
                    row.put("adminReply", reply);
                    row.put("replyUpdatedAt", replyUpdatedAt);
                }
                rows.add(row);
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

    /** 顶栏轻量查询未读数量，页面隐藏时前端暂停轮询 */
    @Transactional(readOnly = true)
    public DataResponse unread() {
        Integer userId = CommonMethod.getUserId();
        boolean administrator = isAdministrator(userId);
        return CommonMethod.getReturnData(messages.unreadCount(userId, administrator));
    }

    /** 已读回执使用账户和消息共同编号，相同消息重复读取保持原回执 */
    private void receipt(int userId, String messageId, Instant now) {
        MessageReceipt receipt = new MessageReceipt();
        receipt.setReceiptId(userId + ":" + messageId);
        receipt.setUserId(userId);
        receipt.setMessageId(messageId);
        receipt.setReadAt(now);
        receipts.save(receipt);
    }

    /** 阅读单条消息先检查接收范围，跨账户请求保持原状态 */
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

    /** 全部已读以请求开始时间为界，分批刷新回执后再读取下一批 */
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

    /** 公告发布核对数据库中的管理员身份，原请求重试返回原公告编号 */
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
        SiteMessage message = new SiteMessage();
        message.setMessageId(id);
        message.setSenderId(actor.getUserId());
        message.setSenderName(actor.getUserName());
        message.setKind("announcement");
        message.setTitle(title.trim());
        message.setContent(content.trim());
        message.setCreatedAt(now);
        messages.save(message);
        return CommonMethod.getReturnData(id, "公告已发布");
    }

    /** 保存反馈正文，消息只向管理员和提交者开放 */
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
        SiteMessage message = new SiteMessage();
        message.setMessageId(id);
        message.setSenderId(sender.getUserId());
        message.setSenderName(sender.getUserName());
        message.setKind("feedback");
        message.setTitle(title.trim());
        message.setContent(content.trim());
        message.setCreatedAt(now);
        messages.save(message);
        receipt(sender.getUserId(), id, now);
        return CommonMethod.getReturnData(id, "反馈已发送");
    }

    /** 修改本人反馈或管理员可管理的公告，并让收件人重新看到未读状态 */
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

    /** 管理员保存反馈处理决定和 Markdown 回复 */
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

    /** 整理管理员回复结果，供页面立即更新处理状态 */
    private DataResponse feedbackResult(String id, FeedbackResolution resolution) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("messageId", id);
        result.put("decision", resolution.getDecision());
        result.put("reply", resolution.getReply());
        result.put("updatedAt", resolution.getUpdatedAt());
        return CommonMethod.getReturnData(result, "反馈处理已保存");
    }
}
