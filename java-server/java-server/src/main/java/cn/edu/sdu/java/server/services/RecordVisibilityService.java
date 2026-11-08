package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.GameRecord;
import cn.edu.sdu.java.server.models.RecordVisibility;
import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.DataRequest;
import cn.edu.sdu.java.server.payload.response.DataResponse;
import cn.edu.sdu.java.server.repositorys.GameRecordRepository;
import cn.edu.sdu.java.server.repositorys.RecordVisibilityRepository;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import cn.edu.sdu.java.server.util.CommonMethod;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 记录可见性服务
 *
 * 记录列表按当前账户隐藏条目
 * 统计接口继续返回完整记录
 */
@Service
public class RecordVisibilityService {
    private final GameRecordRepository records;
    private final RecordVisibilityRepository visibility;
    private final UserRepository users;

    /**
     * 依赖注入
     * 原记录用于核对访问权限，独立仓库只保存隐藏状态
     */
    public RecordVisibilityService(GameRecordRepository records,
            RecordVisibilityRepository visibility, UserRepository users) {
        this.records = records;
        this.visibility = visibility;
        this.users = users;
    }

    /**
     * 标记隐藏状态
     * 为返回资料标记当前账户的隐藏选择，原列表和记录数量继续保留
     */
    @Transactional(readOnly = true)
    public void mark(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Integer> recordIds = new ArrayList<Integer>();
        for (Map<String, Object> row : rows) {
            recordIds.add((Integer) row.get("recordId"));
        }
        Set<Integer> hidden = new HashSet<Integer>();
        int userId = CommonMethod.getUserId();
        for (RecordVisibility item : visibility.findByUserIdAndRecordIdIn(userId, recordIds)) {
            hidden.add(item.getRecordId());
        }
        for (Map<String, Object> row : rows) {
            row.put("hidden", hidden.contains(row.get("recordId")));
        }
    }

    /**
     * 修改可见性状态
     * 隐藏与恢复共用编号、权限和账户锁，只修改当前查看账户的偏好
     */
    private DataResponse change(DataRequest request, boolean hidden) {
        int recordId;
        try {
            recordId = Integer.parseInt(request.getString("recordId"));
        } catch (NumberFormatException error) {
            return CommonMethod.getReturnMessageError("记录编号不正确");
        }
        if (recordId < 1) {
            return CommonMethod.getReturnMessageError("记录编号不正确");
        }
        int userId = CommonMethod.getUserId();
        if (users.lockAccount(userId).isEmpty()) {
            return CommonMethod.getReturnMessageError("账户不存在");
        }
        Optional<GameRecord> found = records.findById(recordId);
        if (found.isEmpty()) {
            return CommonMethod.getReturnMessageError("记录不存在");
        }
        GameRecord record = found.get();
        User owner = record.getUser();
        if (!"ROLE_ADMIN".equals(CommonMethod.getRoleName())
                && !owner.getUserId().equals(userId)) {
            return CommonMethod.getReturnMessageError("仅可隐藏自己的记录");
        }
        String id = userId + ":" + recordId;
        boolean exists = visibility.existsById(id);
        if (hidden && !exists) {
            RecordVisibility item = new RecordVisibility();
            item.setVisibilityId(id);
            item.setUserId(userId);
            item.setRecordId(recordId);
            item.setHiddenAt(Instant.now());
            visibility.save(item);
        } else if (!hidden && exists) {
            visibility.deleteById(id);
        }
        return CommonMethod.getReturnMessageOK();
    }

    /**
     * 隐藏记录
     * 隐藏只写入查看偏好，同一账户重复请求由账户锁串行处理
     */
    @Transactional
    public DataResponse hide(DataRequest request) {
        return change(request, true);
    }

    /**
     * 恢复记录显示
     * 恢复只移除当前查看账户的隐藏选择，原记录与其他账户选择继续保留
     */
    @Transactional
    public DataResponse restore(DataRequest request) {
        return change(request, false);
    }
}
