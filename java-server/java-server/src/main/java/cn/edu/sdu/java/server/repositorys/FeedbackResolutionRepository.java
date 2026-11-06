package cn.edu.sdu.java.server.repositorys;

import cn.edu.sdu.java.server.models.FeedbackResolution;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 按消息编号保存反馈处理结果，列表查询只读取当前页结果 */
public interface FeedbackResolutionRepository
        extends JpaRepository<FeedbackResolution, String> {
    /** 一次读取当前页反馈的处理结果 */
    List<FeedbackResolution> findByMessageIdIn(List<String> messageIds);
}
