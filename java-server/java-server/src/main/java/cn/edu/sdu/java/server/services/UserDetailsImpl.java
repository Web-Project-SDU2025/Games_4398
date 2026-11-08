package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
/**
 * 用户认证详情实现类
 *
 * 保存认证期间的账户快照
 * 权限由当前数据库角色生成
 */
@Getter
@Setter
public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private Integer id;
    private String username;
    private String perName;

    @JsonIgnore
    private String password;

    private Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造函数
     * 保存认证字段，密码哈希在JSON输出中由注解隐藏
     */
    public UserDetailsImpl(Integer id, String username, String password,String perName,
                           Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.perName = perName;
        this.authorities = authorities;
    }

    /**
     * 构建用户详情
     * 根据账户创建认证快照，角色来自关联的账户类型
     */
    public static UserDetailsImpl build(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(user.getUserType().getName()));

        return new UserDetailsImpl(
                user.getUserId(),
                user.getUserName(),
                user.getPassword(),
                user.getName(),
                authorities);
    }

    /**
     * 获取权限集合
     * 返回此账户的权限集合，供方法权限规则读取
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 获取密码
     * 返回密码哈希，供编码器执行密码匹配
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 获取用户名
     * 返回登录账号，供令牌主体与当前账户比较
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期
     * 当前账户模型统一使用有效账号，过期状态沿用固定结果
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定
     * 当前模型统一使用解锁状态，角色检查由权限规则处理
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期
     * 当前模型统一使用有效凭证状态，令牌有效期单独校验
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否启用
     * 当前模型统一使用启用状态，账户删除由用户查询识别
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 比较对象是否相等
     * 按账户编号比较身份，空对象和其他类型返回false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        UserDetailsImpl user = (UserDetailsImpl) o;
        return Objects.equals(id, user.id);
    }

    /**
     * 生成哈希码
     * 使用账户编号生成哈希，与equals的身份比较保持一致
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
