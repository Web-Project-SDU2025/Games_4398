package cn.edu.sdu.java.server.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 保存当前账户的桌宠状态，由宠物房服务改变数值 */
@Entity
@Table(name = "pet_room")
@Getter
@Setter
public class PetRoom {
    @Id
    private Integer userId;
    private int fullness;
    private int bond;
    private int energy;
    private int experience;
    private int food;
}
