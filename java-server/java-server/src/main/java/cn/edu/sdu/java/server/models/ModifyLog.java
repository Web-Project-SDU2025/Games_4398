package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(	name = "modify_log",
        uniqueConstraints = {
        })
public class ModifyLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = 4)
    private String type;
    @Size(max = 20)
    private String tableName;
    @Size(max = 2000)
    private String info;
    @Size(max = 20)
    private String operateTime;
    private Integer operatorId;

}
