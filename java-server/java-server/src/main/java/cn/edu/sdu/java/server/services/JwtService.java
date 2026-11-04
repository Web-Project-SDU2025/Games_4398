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

/** 生成和验证登录令牌，密钥与有效期由后端配置提供 */
@Service
public class JwtService {
    @Value("${security.jwt.secret-key}")
    private String secretKey;
    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    /** 验证签名并读取令牌中的账号名称 */
    public String extractUsername(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }

    /** 根据账户生成带有效期的登录令牌 */
    public String generateToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder();
        builder.setSubject(userDetails.getUsername());
        builder.setIssuedAt(new Date(now));
        builder.setExpiration(new Date(now + jwtExpiration));
        builder.signWith(getSignInKey(), SignatureAlgorithm.HS256);
        return builder.compact();
    }

    /** 校验令牌账号和有效期，签名错误由解析器抛出异常 */
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

    /** 解析令牌并验证签名，返回其中的账户和有效期字段 */
    private Claims extractAllClaims(String token) {
        JwtParserBuilder builder = Jwts.parserBuilder();
        builder.setSigningKey(getSignInKey());
        JwtParser parser = builder.build();
        Jws<Claims> parsed = parser.parseClaimsJws(token);
        return parsed.getBody();
    }

    /** 将配置中的 Base64 密钥转换为签名密钥 */
    private Key getSignInKey() {
        byte[] bytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(bytes);
    }
}
