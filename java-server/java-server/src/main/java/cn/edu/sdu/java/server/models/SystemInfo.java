package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(	name = "system_info",
        uniqueConstraints = {
        })
    public class SystemInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = 40)
    String name;
    @Size(max = 40)
    String value;
    @Size(max = 100)
    String des;

}
