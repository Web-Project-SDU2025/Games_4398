package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.SiteMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 个人通知限定接收账户，公告和反馈按角色及回执判断可见状态 */
public interface SiteMessageRepository extends JpaRepository<SiteMessage, String> {
    /** 分页查询当前账户可见消息，反馈只对管理员和提交者开放 */
    @Query("select message from SiteMessage message "
        + "where (message.recipientId = :userId "
        + "or (message.recipientId is null and message.kind = 'announcement') "
        + "or (message.recipientId is null and message.kind = 'feedback' "
        + "and (:administrator = true or message.senderId = :userId))) "
        + "and (:kind = 'all' or message.kind = :kind) "
        + "and (:unreadOnly = false or not exists (select receipt.receiptId from MessageReceipt receipt "
        + "where receipt.userId = :userId and receipt.messageId = message.messageId)) "
        + "order by message.createdAt desc, message.messageId desc")
    Page<SiteMessage> inbox(@Param("userId") Integer userId,
        @Param("administrator") boolean administrator, @Param("kind") String kind,
        @Param("unreadOnly") boolean unreadOnly, Pageable page);

    /** 未读数量只统计当前账户能够阅读的消息 */
    @Query("select count(message) from SiteMessage message "
        + "where (message.recipientId = :userId "
        + "or (message.recipientId is null and message.kind = 'announcement') "
        + "or (message.recipientId is null and message.kind = 'feedback' "
        + "and (:administrator = true or message.senderId = :userId))) "
        + "and not exists (select receipt.receiptId from MessageReceipt receipt "
        + "where receipt.userId = :userId and receipt.messageId = message.messageId)")
    long unreadCount(@Param("userId") Integer userId,
        @Param("administrator") boolean administrator);

    /** 全部已读按固定开始时间分批处理，之后的新消息继续保持未读 */
    @Query("select message from SiteMessage message "
        + "where (message.recipientId = :userId "
        + "or (message.recipientId is null and message.kind = 'announcement') "
        + "or (message.recipientId is null and message.kind = 'feedback' "
        + "and (:administrator = true or message.senderId = :userId))) "
        + "and message.createdAt <= :cutoff "
        + "and not exists (select receipt.receiptId from MessageReceipt receipt "
        + "where receipt.userId = :userId and receipt.messageId = message.messageId) "
        + "order by message.createdAt, message.messageId")
    List<SiteMessage> unreadBatch(@Param("userId") Integer userId,
        @Param("administrator") boolean administrator, @Param("cutoff") Instant cutoff,
        Pageable page);

    /** 读取同一管理员的最近公告，发布流程先锁定管理员账户 */
    SiteMessage findFirstBySenderIdAndKindOrderByCreatedAtDesc(Integer senderId, String kind);
}
