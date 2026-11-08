package cn.edu.sdu.java.server.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.util.Date;

/**
 * JWT令牌服务
 *
 * 生成和验证登录令牌
 * 密钥和有效期由配置文件提供
 */
@Service
public class JwtService {
    @Value("${security.jwt.secret-key}")
    private String secretKey;
    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    /**
     * 提取用户名
     * 验证签名并从令牌中读取账号名称
     */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    /**
     * 生成JWT令牌
     * 根据用户信息生成带有效期的登录令牌
     */
    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder();
        builder.setSubject(userDetails.getUsername());
        builder.setIssuedAt(new Date(now));
        builder.setExpiration(new Date(now + jwtExpiration));
        builder.signWith(getSignInKey(), SignatureAlgorithm.HS256);
        return builder.compact();
    }

    /**
     * 验证令牌有效性
     * 检查令牌中的账号和有效期，签名错误会抛出异常
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String username = claims.getSubject();
        Date expiration = claims.getExpiration();
        if (username == null || expiration == null) {
            return false;
        }
        if (!username.equals(userDetails.getUsername())) {
            return false;
        }
        return expiration.after(new Date());
    }

    /**
     * 解析令牌内容
     * 验证签名并提取账户和有效期字段
     */
    private Claims extractAllClaims(String token) {
        JwtParserBuilder builder = Jwts.parserBuilder();
        builder.setSigningKey(getSignInKey());
        JwtParser parser = builder.build();
        Jws<Claims> parsed = parser.parseClaimsJws(token);
        return parsed.getBody();
    }

    /**
     * 获取签名密钥
     * 将配置中的Base64密钥转换为签名密钥
     */
    private Key getSignInKey() {
        byte[] bytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(bytes);
    }
}
