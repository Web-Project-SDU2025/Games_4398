package cn.edu.sdu.java.server.services;

import cn.edu.sdu.java.server.models.User;
import cn.edu.sdu.java.server.payload.request.LoginRequest;
import cn.edu.sdu.java.server.repositorys.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;

    public AuthenticationService(
        UserRepository userRepository,
        AuthenticationManager authenticationManager
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }


    public User authenticate(LoginRequest input) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                input.getUsername(),
                input.getPassword()
            )
        );

        return userRepository.findByUserName(input.getUsername()).orElseThrow();
    }

}
