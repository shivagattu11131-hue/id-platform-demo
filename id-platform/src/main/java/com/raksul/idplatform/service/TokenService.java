package com.raksul.idplatform.service;

import com.raksul.idplatform.config.JwksConfig;
import com.raksul.idplatform.model.AuthToken;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.repository.AuthTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
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

    @Value("${id-platform.jwt.id-token-expiry:600000}")
    private long idTokenExpiry;

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

    public String generateIdToken(User user, String clientId, String nonce, String accessToken) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(idTokenExpiry);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getDisplayName())
                .claim("email_verified", true)
                .claim("type", "id")
                .issuer(issuer)
                .audience().add(clientId).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(jwksConfig.getPrivateKey());

        if (nonce != null) {
            builder.claim("nonce", nonce);
        }

        if (accessToken != null) {
            String atHash = computeAtHash(accessToken);
            if (atHash != null) {
                builder.claim("at_hash", atHash);
            }
        }

        return builder.compact();
    }

    private String computeAtHash(String accessToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            byte[] leftHalf = new byte[hash.length / 2];
            System.arraycopy(hash, 0, leftHalf, 0, leftHalf.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return null;
        }
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
