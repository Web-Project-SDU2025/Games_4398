package cn.edu.sdu.java.server.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity
@Table(	name = "request_log",
        uniqueConstraints = {
        })
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Size(max = 100)
    private String url;
    @Size(max = 20)
    private String username;
    @Size(max = 20)
    private String startTime;
    private Double requestTime;
}
