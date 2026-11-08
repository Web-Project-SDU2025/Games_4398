package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 记录可见性实体类
 *
 * 保存用户隐藏的游戏记录编号
 * 原记录本身还在，继续用于计时、统计和奖励，只是列表里不显示
 */
@Entity
@Table(name = "record_visibility")
@Getter
@Setter
public class RecordVisibility {
    /** 可见性ID，主键 */
    @Id
    private String visibilityId;

    /** 用户ID */
    private Integer userId;

    /** 被隐藏的记录ID */
    private Integer recordId;

    /** 隐藏时间 */
    private Instant hiddenAt;
}
