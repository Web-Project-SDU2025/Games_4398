package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/** 按账号加载认证资料，账号缺失时交给框架报告认证失败 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    /** 按登录名读取账户，找不到时返回认证失败 */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> found = userRepository.findByUserName(username);
        if (found.isEmpty()) {
            throw new UsernameNotFoundException("账号不存在：" + username);
        }
        User user = found.get();
        return UserDetailsImpl.build(user);
    }

}
