package com.cqu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtProperties {

    @Value("${jwt.secret-key}")
    private String jwtSecretKey;

    private static final long ACCESS_EXPIRATION_TIME = 15 * 60 * 60 * 1000L; // 15小时

    public String createAccessToken(Map<String, Object> claims) {

        //创建密钥即Key对象
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        return Jwts.builder()
                .setClaims(claims)
                // 设置过期时间
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION_TIME))
                // 设置签名使用的签名秘钥
                .signWith(secretKeySpec)
                .compact();
    }

    public Claims parseJWT(String token) {

        //创建密钥即Key对象
        SecretKeySpec secretKeySpec = new SecretKeySpec(jwtSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        return Jwts.parserBuilder()
                .setSigningKey(secretKeySpec)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
