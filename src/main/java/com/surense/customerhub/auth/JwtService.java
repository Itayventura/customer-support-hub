package com.surense.customerhub.auth;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = new SecretKeySpec(
                properties.secret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    public IssuedToken issueToken(String username, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(properties.ttlMinutes()));

        String token = Jwts.builder()
                .subject(username)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", roles)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedToken(token, expiresAt, properties.ttlMinutes() * 60L);
    }

    SecretKey signingKey() {
        return signingKey;
    }

    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
