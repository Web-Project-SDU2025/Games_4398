package cn.edu.sdu.java.server.models;
/*
 * DictionaryInfo 数据字典实体类，保存字典的基本信息，数据库表名 dictionary
 * Integer id 数据字典表  dictionary 主键 id
 * Integer pid  父节点ID  所属于的字典类型的id
 * String value 字典值
 * String label 字典名
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
@Entity

@Table(	name = "dictionary",
        uniqueConstraints = {
        })
public class DictionaryInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer pid;

    @Size(max = 40)
    private String value;

    @Size(max = 40)
    private String label;
}
