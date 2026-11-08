package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.MessageReceipt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 消息回执数据访问接口
 *
 * 消息回执按账户隔离
 * 列表只读取当前页所需状态
 */
public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, String> {
    /**
     * 批量查询回执
     * 一次读取当前页消息的回执，避免逐条查询
     */
    List<MessageReceipt> findByUserIdAndMessageIdIn(Integer userId, List<String> messageIds);

    /**
     * 清除消息回执
     * 修改消息后清除旧回执，让收件人重新看到未读状态
     */
    void deleteByMessageId(String messageId);

    /**
     * 清除指定用户的回执
     * 管理员回复反馈后只清除提交者的已读回执
     */
    void deleteByUserIdAndMessageId(Integer userId, String messageId);
}
