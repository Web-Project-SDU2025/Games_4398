package com.game.system.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * UserDetailsServiceImpl Spring Security用户加载服务
 * 实现UserDetailsService接口，用于从数据库加载用户信息
 *
 * 注意：这是一个基础框架，需要在实现User实体和UserRepository后完善
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    // TODO: 注入UserRepository
    // private final UserRepository userRepository;
    //
    // public UserDetailsServiceImpl(UserRepository userRepository) {
    //     this.userRepository = userRepository;
    // }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO: 从数据库加载用户
        // User user = userRepository.findByUsername(username)
        //     .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        //
        // // 获取用户角色
        // String roleName = user.getRole() != null ? user.getRole().getCode() : "ROLE_USER";
        //
        // return UserDetailsImpl.build(user, roleName);

        // 临时返回null，等User实体创建后再实现
        throw new UsernameNotFoundException("UserDetailsServiceImpl需要在User实体创建后实现");
    }
}
