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

    // TODO: [数据库连接] 注入UserRepository，用于从数据库查询用户信息
    // 步骤：
    // 1. 先创建 User 实体类（entity/User.java）
    // 2. 创建 UserRepository 接口（repository/UserRepository.java）
    // 3. 取消下面代码的注释，注入 UserRepository
    //
    // private final UserRepository userRepository;
    //
    // public UserDetailsServiceImpl(UserRepository userRepository) {
    //     this.userRepository = userRepository;
    // }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO: [数据库连接] 从数据库加载用户信息并返回 UserDetails
        // 步骤：
        // 1. 使用 userRepository.findByUsername(username) 从数据库查询用户
        // 2. 如果用户不存在，抛出 UsernameNotFoundException
        // 3. 获取用户的角色信息（user.getRole().getCode()）
        // 4. 调用 UserDetailsImpl.build(user, roleName) 构建 UserDetails 对象
        // 5. 返回 UserDetails 对象给 Spring Security 进行认证
        //
        // 实现代码示例：
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
