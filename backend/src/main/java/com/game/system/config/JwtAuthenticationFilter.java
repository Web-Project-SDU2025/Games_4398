package com.game.system.config;

import com.game.system.service.JwtService;
import com.game.system.service.UserDetailsServiceImpl;
import com.game.system.util.DateTimeTool;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Date;

/**
 * JwtAuthenticationFilter JWT认证过滤器
 * 拦截所有请求，验证JWT令牌，并将用户信息注入到SecurityContext
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver handlerExceptionResolver;
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    @Autowired
    private RequestAttributeSecurityContextRepository repo;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsServiceImpl userDetailsService,
            HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String username = "";
        String url = request.getRequestURI();
        Date startDate = new Date();
        final String authHeader = request.getHeader("Authorization");

        // 如果没有Authorization头或不是Bearer token，直接放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            SecurityContext context = SecurityContextHolder.getContext();
            repo.saveContext(context, request, response);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 提取JWT令牌
            final String jwt = authHeader.substring(7);
            username = jwtService.extractUsername(jwt);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // 如果用户名不为空且当前未认证
            if (username != null && authentication == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // 验证令牌是否有效
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            SecurityContext context = SecurityContextHolder.getContext();
            repo.saveContext(context, request, response);
            filterChain.doFilter(request, response);

            // 记录请求日志
            Date endDate = new Date();
            double requestTime = (int) (endDate.getTime() - startDate.getTime()) / 1000.;
            String startTime = DateTimeTool.formatDateTime(startDate, "yyyy-MM-dd HH:mm:ss");
            logger.info(url + "," + username + "," + startTime + "," + requestTime);

        } catch (Exception exception) {
            exception.printStackTrace();
            handlerExceptionResolver.resolveException(request, response, null, exception);
        }
    }
}
