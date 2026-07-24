package com.raksul.idplatform.service;

import com.raksul.idplatform.config.JwksConfig;
import com.raksul.idplatform.model.AuthToken;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.repository.AuthTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private JwksConfig jwksConfig;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @Value("${id-platform.jwt.access-token-expiry:900000}")
    private long accessTokenExpiry;

    @Value("${id-platform.jwt.refresh-token-expiry:604800000}")
    private long refreshTokenExpiry;

    @Value("${id-platform.issuer}")
    private String issuer;

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(accessTokenExpiry);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("type", "access")
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwksConfig.getPrivateKey())
                .compact();

        AuthToken authToken = new AuthToken(user, token, AuthToken.TokenType.ACCESS, expiry);
        tokenRepository.save(authToken);

        return token;
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(refreshTokenExpiry);

        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("type", "refresh")
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwksConfig.getPrivateKey())
                .compact();

        AuthToken authToken = new AuthToken(user, token, AuthToken.TokenType.REFRESH, expiry);
        tokenRepository.save(authToken);

        return token;
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwksConfig.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void revokeToken(String token) {
        tokenRepository.revokeToken(token);
    }

    @Transactional
    public void revokeAllUserTokens(Long userId) {
        tokenRepository.revokeAllUserTokens(userId);
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }
}
