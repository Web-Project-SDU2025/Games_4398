package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 保存当前账户隐藏的记录编号，原记录继续用于计时、统计和奖励 */
@Entity
@Table(name = "record_visibility")
@Getter
@Setter
public class RecordVisibility {
    @Id
    private String visibilityId;
    private Integer userId;
    private Integer recordId;
    private Instant hiddenAt;
}
