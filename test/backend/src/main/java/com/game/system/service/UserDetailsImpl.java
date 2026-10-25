package com.game.system.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * UserDetailsImpl Spring Security用户详情实现类
 * 实现UserDetails接口，用于Spring Security认证
 */
public class UserDetailsImpl implements UserDetails {

    private Long id;
    private String username;
    private String email;
    private String phone;

    @JsonIgnore
    private String password;

    private Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long id, String username, String email, String phone,
                          String password, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.authorities = authorities;
    }

    /**
     * 根据User实体创建UserDetailsImpl
     * 注意：需要先创建User实体类后才能使用此方法
     */
    public static UserDetailsImpl build(Object user, String roleName) {
        // 这里暂时使用反射或手动转换
        // 等User实体创建后，可以改为：
        // User u = (User) user;
        // List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(u.getRoleName()));
        // return new UserDetailsImpl(u.getId(), u.getUsername(), u.getEmail(), u.getPhone(), u.getPassword(), authorities);

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(roleName));
        return new UserDetailsImpl(null, null, null, null, null, authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
