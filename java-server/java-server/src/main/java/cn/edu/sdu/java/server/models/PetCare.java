package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 保存互动累计分和上次有效抚摸的时间，旧版存档不需要补写 */
@Entity
@Table(name = "pet_care")
@Getter
@Setter
public class PetCare {
    @Id
    private Integer userId;
    private long carePoints;
    private long lastPetAt;
}
