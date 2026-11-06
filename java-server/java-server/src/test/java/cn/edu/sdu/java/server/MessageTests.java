package cn.edu.sdu.java.server;

import cn.edu.sdu.java.server.controllers.AnnouncementController;
import cn.edu.sdu.java.server.controllers.MessagePageController;
import cn.edu.sdu.java.server.controllers.PageController;
import cn.edu.sdu.java.server.models.FeedbackResolution;
import cn.edu.sdu.java.server.models.MessageReceipt;
import cn.edu.sdu.java.server.models.SiteMessage;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.models.UserType;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.MessageReceiptRepository;
import cn.edu.sdu.java.server.repositorys.FeedbackResolutionRepository;
import cn.edu.sdu.java.server.repositorys.SiteMessageRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.services.MessageService;
import cn.edu.sdu.java.server.services.UserDetailsImpl;
import cn.edu.sdu.java.server.util.MessageRules;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 消息与公告使用内存仓库验证权限、已读隔离和发布重试，真实账户保持原值 */
class MessageTests {
    /** 同一公告的重试保持相同操作编号 */
    private static final String OPERATION = "97cc06d0-b281-4c90-8be3-d9bf28b6ec7f";

    /** 每个用例结束后清理认证身份 */
    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** 采用真实认证主体构造当前账户，角色由用例指定 */
    private static User login(int userId, String role) {
        User user = new User();
        UserType type = new UserType();
        type.setName(role);
        user.setUserId(userId);
        user.setUserName("account-" + userId);
        user.setPassword("test");
        user.setUserType(type);
        UserDetailsImpl details = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken identity =
            new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(identity);
        return user;
    }

    /** 构造账户通知，接收编号与当前查看账户分别指定 */
    private SiteMessage message(String id, Integer recipientId) {
        SiteMessage message = new SiteMessage();
        message.setMessageId(id);
        message.setRecipientId(recipientId);
        message.setKind("account");
        message.setTitle("账户通知");
        message.setContent("字块币 +15 BC");
        message.setCreatedAt(Instant.now());
        return message;
    }

    /** 各用例使用全新仓库，消息正文与回执独立保存 */
    private static final class Fixture {
        private final SiteMessageRepository messages = mock(SiteMessageRepository.class);
        private final MessageReceiptRepository receipts = mock(MessageReceiptRepository.class);
        private final FeedbackResolutionRepository resolutions =
            mock(FeedbackResolutionRepository.class);
        private final UserRepository users = mock(UserRepository.class);
        private final MessageService service =
            new MessageService(messages, receipts, resolutions, users);
        private final User actor;

        /** 当前账号锁返回与认证主体相同的管理员实体 */
        Fixture() {
            actor = login(12, "ROLE_ADMIN");
            when(users.lockAccount(12)).thenReturn(Optional.of(actor));
            when(users.findById(12)).thenReturn(Optional.of(actor));
        }

        /** 返回合法公告参数，边界用例按字段修改 */
        DataRequest publication() {
            DataRequest request = new DataRequest();
            request.add("title", "活动公告");
            request.add("content", "欢迎参加本周游戏活动");
            request.add("requestId", OPERATION);
            return request;
        }
    }

    /** 查询参数里的账户编号不会影响实际接收范围，当前页回执决定已读状态 */
    @Test
    void inboxUsesAuthenticatedAccountAndPagedReceipts() {
        Fixture fixture = new Fixture();
        SiteMessage notice = message("coins:reward:one", 12);
        SiteMessage announcement = message("announcement:one", null);
        announcement.setKind("announcement");
        when(fixture.messages.inbox(eq(12), eq(true), eq("all"), eq(false), any()))
            .thenReturn(new PageImpl<SiteMessage>(List.of(notice, announcement)));
        MessageReceipt receipt = new MessageReceipt();
        receipt.setMessageId(notice.getMessageId());
        when(fixture.receipts.findByUserIdAndMessageIdIn(12,
                List.of(notice.getMessageId(), announcement.getMessageId()))).thenReturn(List.of(receipt));
        when(fixture.messages.unreadCount(12, true)).thenReturn(1L);
        DataRequest request = new DataRequest();
        request.add("userId", 99);
        Map<?, ?> result = (Map<?, ?>) fixture.service.list(request).getData();
        List<?> rows = (List<?>) result.get("messages");
        assertEquals(true, ((Map<?, ?>) rows.get(0)).get("read"));
        assertEquals(false, ((Map<?, ?>) rows.get(1)).get("read"));
        assertEquals(1L, result.get("unreadCount"));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(fixture.messages).inbox(eq(12), eq(true), eq("all"), eq(false), page.capture());
        assertEquals(20, page.getValue().getPageSize());
        assertEquals(0, page.getValue().getPageNumber());
        verify(fixture.messages, never()).save(any());
        verify(fixture.receipts, never()).save(any());
    }

    /** 不合法类型、过大页数和小数页码在查询仓库前结束 */
    @Test
    void invalidFiltersKeepRepositoriesUntouched() {
        Fixture fixture = new Fixture();
        DataRequest request = new DataRequest();
        request.add("page", 1.5);
        assertEquals(1, fixture.service.list(request).getCode());
        request.add("page", 1);
        request.add("pageSize", 101);
        assertEquals(1, fixture.service.list(request).getCode());
        request.add("pageSize", 20);
        request.add("kind", "private");
        assertEquals(1, fixture.service.list(request).getCode());
        verifyNoInteractions(fixture.messages, fixture.receipts);
    }

    /** 已读只写当前账户的回执，其他账户私信和重复点击分别处理 */
    @Test
    void readRejectsForeignMessagesAndKeepsReceiptOnce() {
        Fixture fixture = new Fixture();
        SiteMessage notice = message("coins:one", 13);
        when(fixture.messages.findById("coins:one")).thenReturn(Optional.of(notice));
        DataRequest request = new DataRequest();
        request.add("messageId", "coins:one");
        assertEquals(1, fixture.service.read(request).getCode());
        verify(fixture.receipts, never()).save(any());
        notice.setRecipientId(null);
        notice.setKind("announcement");
        assertEquals(0, fixture.service.read(request).getCode());
        ArgumentCaptor<MessageReceipt> saved = ArgumentCaptor.forClass(MessageReceipt.class);
        verify(fixture.receipts).save(saved.capture());
        assertEquals(12, saved.getValue().getUserId());
        assertEquals("12:coins:one", saved.getValue().getReceiptId());
        when(fixture.receipts.existsById("12:coins:one")).thenReturn(true);
        assertEquals(0, fixture.service.read(request).getCode());
        verify(fixture.receipts, times(1)).save(any());
        verify(fixture.messages, never()).save(any());
    }

    /** 全部已读分批写回执，每批使用相同开始时间且已读选择只属于当前账户 */
    @Test
    void readAllUsesStableCutoffAndBatches() {
        Fixture fixture = new Fixture();
        when(fixture.messages.unreadBatch(eq(12), eq(true), any(), any()))
            .thenReturn(List.of(message("first", 12)), List.of(message("second", null)), List.of());
        assertEquals(0, fixture.service.readAll().getCode());
        ArgumentCaptor<Instant> times = ArgumentCaptor.forClass(Instant.class);
        verify(fixture.messages, times(3)).unreadBatch(
            eq(12), eq(true), times.capture(), any());
        assertEquals(times.getAllValues().get(0), times.getAllValues().get(2));
        verify(fixture.receipts, times(2)).save(any());
        verify(fixture.receipts, times(2)).flush();
    }

    /** 相同编号和内容重试返回原公告，修改内容复用编号时返回输入提示 */
    @Test
    void announcementRetriesPublishOnlyOnce() {
        Fixture fixture = new Fixture();
        DataRequest request = fixture.publication();
        assertEquals(0, fixture.service.publish(request).getCode());
        ArgumentCaptor<SiteMessage> saved = ArgumentCaptor.forClass(SiteMessage.class);
        verify(fixture.messages).save(saved.capture());
        SiteMessage publication = saved.getValue();
        assertNull(publication.getRecipientId());
        assertEquals("announcement", publication.getKind());
        assertEquals(12, publication.getSenderId());
        when(fixture.messages.findById(publication.getMessageId())).thenReturn(Optional.of(publication));
        assertEquals(0, fixture.service.publish(request).getCode());
        request.add("content", "另一个公告");
        assertEquals(1, fixture.service.publish(request).getCode());
        verify(fixture.messages, times(1)).save(any());
    }

    /** 管理员降权、普通角色、内容超长及过密发布都在保存前返回 */
    @Test
    void announcementValidationAndFrequencyAreEnforced() {
        Fixture fixture = new Fixture();
        login(12, "ROLE_GAME_USER");
        assertEquals(1, fixture.service.publish(fixture.publication()).getCode());
        login(12, "ROLE_ADMIN");
        DataRequest request = fixture.publication();
        request.add("requestId", "invalid");
        assertEquals(1, fixture.service.publish(request).getCode());
        request = fixture.publication();
        request.add("content", "字".repeat(MessageRules.CONTENT_LIMIT + 1));
        assertEquals(1, fixture.service.publish(request).getCode());
        fixture.actor.getUserType().setName("ROLE_GAME_USER");
        assertEquals(1, fixture.service.publish(fixture.publication()).getCode());
        fixture.actor.getUserType().setName("ROLE_ADMIN");
        when(fixture.messages.findFirstBySenderIdAndKindOrderByCreatedAtDesc(12, "announcement"))
            .thenReturn(message("latest", null));
        assertEquals(1, fixture.service.publish(fixture.publication()).getCode());
        verify(fixture.messages, never()).save(any());
    }

    /** 反馈保存一份正文，提交者和管理员都能查看且重试不会重复 */
    @Test
    void feedbackIsVisibleToSenderAndAdministrators() {
        Fixture fixture = new Fixture();
        User sender = login(21, "ROLE_GAME_USER");
        when(fixture.users.lockAccount(21)).thenReturn(Optional.of(sender));
        when(fixture.users.findById(21)).thenReturn(Optional.of(sender));
        when(fixture.users.findByUserTypeName("ROLE_ADMIN"))
            .thenReturn(List.of(fixture.actor));
        String id = "feedback:21:" + OPERATION;
        when(fixture.messages.findById(id)).thenReturn(Optional.empty());
        DataRequest request = new DataRequest();
        request.add("requestId", OPERATION);
        request.add("title", "游戏商城问题");
        request.add("content", "**反馈正文**");

        assertEquals(0, fixture.service.feedback(request).getCode());
        ArgumentCaptor<SiteMessage> saved = ArgumentCaptor.forClass(SiteMessage.class);
        verify(fixture.messages).save(saved.capture());
        SiteMessage feedback = saved.getValue();
        assertEquals("feedback", feedback.getKind());
        assertNull(feedback.getRecipientId());
        assertEquals(21, feedback.getSenderId());
        assertEquals("account-21", feedback.getSenderName());
        assertEquals("**反馈正文**", feedback.getContent());
        verify(fixture.receipts).save(any());

        when(fixture.messages.findById(id)).thenReturn(Optional.of(feedback));
        assertEquals(0, fixture.service.feedback(request).getCode());
        verify(fixture.messages, times(1)).save(any());

        when(fixture.messages.inbox(eq(21), eq(false), eq("feedback"), eq(false), any()))
            .thenReturn(new PageImpl<SiteMessage>(List.of(feedback)));
        DataRequest filter = new DataRequest();
        filter.add("kind", "feedback");
        assertEquals(1, ((List<?>) ((Map<?, ?>) fixture.service.list(filter).getData())
            .get("messages")).size());
        verify(fixture.messages).inbox(eq(21), eq(false), eq("feedback"), eq(false), any());

        login(12, "ROLE_ADMIN");
        when(fixture.messages.inbox(eq(12), eq(true), eq("feedback"), eq(false), any()))
            .thenReturn(new PageImpl<SiteMessage>(List.of(feedback)));
        assertEquals(1, ((List<?>) ((Map<?, ?>) fixture.service.list(filter).getData())
            .get("messages")).size());
        verify(fixture.messages).inbox(eq(12), eq(true), eq("feedback"), eq(false), any());
    }

    /** 管理员可编辑公告，反馈只有提交账户本人可以编辑 */
    @Test
    void announcementsAndFeedbackCanBeEditedByTheirAuthors() {
        Fixture fixture = new Fixture();
        SiteMessage announcement = message("announcement:one", null);
        announcement.setKind("announcement");
        when(fixture.messages.findById("announcement:one"))
            .thenReturn(Optional.of(announcement));
        DataRequest announcementEdit = new DataRequest();
        announcementEdit.add("messageId", "announcement:one");
        announcementEdit.add("title", "更新后的公告");
        announcementEdit.add("content", "**更新内容**");

        assertEquals(0, fixture.service.edit(announcementEdit).getCode());
        assertEquals("**更新内容**", announcement.getContent());
        verify(fixture.receipts).deleteByMessageId("announcement:one");

        User sender = login(21, "ROLE_GAME_USER");
        when(fixture.users.lockAccount(21)).thenReturn(Optional.of(sender));
        SiteMessage feedback = message("feedback:21:" + OPERATION, null);
        feedback.setKind("feedback");
        feedback.setSenderId(21);
        FeedbackResolution oldResolution = new FeedbackResolution();
        oldResolution.setMessageId(feedback.getMessageId());
        oldResolution.setDecision(MessageRules.FEEDBACK_WILL_FIX);
        oldResolution.setAdministratorId(12);
        oldResolution.setReply("旧回复");
        oldResolution.setUpdatedAt(Instant.now());
        when(fixture.messages.findById(feedback.getMessageId()))
            .thenReturn(Optional.of(feedback));
        when(fixture.resolutions.findById(feedback.getMessageId()))
            .thenReturn(Optional.of(oldResolution));
        DataRequest feedbackEdit = new DataRequest();
        feedbackEdit.add("messageId", feedback.getMessageId());
        feedbackEdit.add("title", "更新后的反馈");
        feedbackEdit.add("content", "已补充更多细节");

        assertEquals(0, fixture.service.edit(feedbackEdit).getCode());
        assertEquals("已补充更多细节", feedback.getContent());
        verify(fixture.receipts).deleteByMessageId(feedback.getMessageId());
        verify(fixture.resolutions).delete(oldResolution);

        User other = login(22, "ROLE_GAME_USER");
        when(fixture.users.lockAccount(22)).thenReturn(Optional.of(other));
        assertEquals(1, fixture.service.edit(feedbackEdit).getCode());
        verify(fixture.messages, times(2)).save(any());
    }

    /** 管理员保存决定和 Markdown 回复，反馈提交者收到新的未读状态 */
    @Test
    void administratorCanResolveFeedbackAndNotifySender() {
        Fixture fixture = new Fixture();
        SiteMessage feedback = message("feedback:21:one", null);
        feedback.setKind("feedback");
        feedback.setSenderId(21);
        when(fixture.messages.findById(feedback.getMessageId()))
            .thenReturn(Optional.of(feedback));
        DataRequest request = new DataRequest();
        request.add("messageId", feedback.getMessageId());
        request.add("decision", MessageRules.FEEDBACK_WILL_FIX);
        request.add("reply", "已记录，**下个版本修复**");

        DataResponse response = fixture.service.respondFeedback(request);

        assertEquals(0, response.getCode());
        ArgumentCaptor<FeedbackResolution> saved =
            ArgumentCaptor.forClass(FeedbackResolution.class);
        verify(fixture.resolutions).save(saved.capture());
        FeedbackResolution resolution = saved.getValue();
        assertEquals(MessageRules.FEEDBACK_WILL_FIX, resolution.getDecision());
        assertEquals("已记录，**下个版本修复**", resolution.getReply());
        assertEquals(12, resolution.getAdministratorId());
        verify(fixture.receipts).deleteByUserIdAndMessageId(21, feedback.getMessageId());

        request.add("decision", MessageRules.FEEDBACK_RESOLVED);
        request.add("reply", "问题已解决");
        assertEquals(0, fixture.service.respondFeedback(request).getCode());
        verify(fixture.resolutions, times(2)).save(any());

        User sender = login(21, "ROLE_GAME_USER");
        when(fixture.users.lockAccount(21)).thenReturn(Optional.of(sender));
        when(fixture.messages.findById(feedback.getMessageId()))
            .thenReturn(Optional.of(feedback));
        request.add("decision", MessageRules.FEEDBACK_WILL_NOT_FIX);
        assertEquals(1, fixture.service.respondFeedback(request).getCode());
        verify(fixture.resolutions, times(2)).save(any());
    }

    /** 未处理反馈显示待处理状态，回复后返回管理员决定和 Markdown 正文 */
    @Test
    void feedbackReplyIsAvailableToTheSender() {
        Fixture fixture = new Fixture();
        SiteMessage feedback = message("feedback:21:one", null);
        feedback.setKind("feedback");
        feedback.setSenderId(21);
        when(fixture.messages.inbox(eq(21), eq(false), eq("feedback"), eq(false), any()))
            .thenReturn(new PageImpl<SiteMessage>(List.of(feedback)));
        when(fixture.users.findById(21)).thenReturn(
            Optional.of(login(21, "ROLE_GAME_USER")));
        DataRequest filter = new DataRequest();
        filter.add("kind", "feedback");

        Map<?, ?> result = (Map<?, ?>) fixture.service.list(filter).getData();
        List<?> rows = (List<?>) result.get("messages");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals(MessageRules.FEEDBACK_PENDING, row.get("feedbackDecision"));
        assertEquals("", row.get("adminReply"));

        FeedbackResolution resolution = new FeedbackResolution();
        resolution.setMessageId(feedback.getMessageId());
        resolution.setDecision(MessageRules.FEEDBACK_WILL_NOT_FIX);
        resolution.setAdministratorId(12);
        resolution.setReply("当前版本暂不支持");
        resolution.setUpdatedAt(Instant.now());
        when(fixture.resolutions.findByMessageIdIn(List.of(feedback.getMessageId())))
            .thenReturn(List.of(resolution));

        result = (Map<?, ?>) fixture.service.list(filter).getData();
        rows = (List<?>) result.get("messages");
        row = (Map<?, ?>) rows.get(0);
        assertEquals(MessageRules.FEEDBACK_WILL_NOT_FIX, row.get("feedbackDecision"));
        assertEquals("当前版本暂不支持", row.get("adminReply"));
        assertEquals(resolution.getUpdatedAt(), row.get("replyUpdatedAt"));
    }

    /** 公告控制器的方法权限实际拦截普通用户，服务替身只接收管理员请求 */
    @Test
    void announcementControllerRequiresAdministrator() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(SecurityFixture.class)) {
            AnnouncementController controller = context.getBean(AnnouncementController.class);
            MessageService service = context.getBean(MessageService.class);
            login(12, "ROLE_GAME_USER");
            try {
                controller.publish(new DataRequest());
                fail("普通账户应被公告发布接口拒绝");
            } catch (AccessDeniedException error) {
                assertNotNull(error);
            }
            verifyNoInteractions(service);
            login(12, "ROLE_ADMIN");
            controller.publish(new DataRequest());
            verify(service).publish(any());
        }
    }

    /** 直接打开或刷新消息中心地址仍返回 Vue 入口 */
    @Test
    void messagePageAddressForwardsToApplication() throws Exception {
        MockMvc client = MockMvcBuilders.standaloneSetup(new MessagePageController()).build();
        client.perform(get("/Messages"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));
        MockMvc generalPages = MockMvcBuilders.standaloneSetup(new PageController()).build();
        generalPages.perform(get("/Messages"))
            .andExpect(status().isNotFound());
    }

    /** 仅加载权限配置与真实发布控制器，外部业务由替身承接 */
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class SecurityFixture {
        /** 使用服务替身记录通过权限校验的调用 */
        @Bean
        MessageService service() {
            return mock(MessageService.class);
        }

        /** 保留控制器的管理员权限声明 */
        @Bean
        AnnouncementController controller(MessageService service) {
            return new AnnouncementController(service);
        }
    }
}
