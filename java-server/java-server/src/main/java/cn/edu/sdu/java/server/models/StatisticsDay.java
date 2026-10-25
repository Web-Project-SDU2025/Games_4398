package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter@Entity
@Table(	name = "statistics_day",
        uniqueConstraints = {
        })
public class StatisticsDay {
    @Id
    @Size(max = 10)
    private String  day;
    private Integer loginCount;
    private Integer requestCount;
    private Integer createCount;
    private Integer modifyCount;

}
