package com.raksul.idplatform.service;

import com.raksul.idplatform.config.JwksConfig;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.repository.UserRepository;
import com.raksul.idplatform.repository.AuthTokenRepository;
import com.raksul.idplatform.model.AuthToken;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

@Service
public class OidcService {

    private static final Logger log = LoggerFactory.getLogger(OidcService.class);

    @Autowired
    private JwksConfig jwksConfig;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository tokenRepository;

    @Autowired
    private TokenService tokenService;

    @Value("${id-platform.issuer}")
    private String issuer;

    public Map<String, Object> getDiscoveryDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/oauth2/authorize");
        doc.put("token_endpoint", issuer + "/oauth2/token");
        doc.put("userinfo_endpoint", issuer + "/oauth2/userinfo");
        doc.put("jwks_uri", issuer + "/jwks.json");
        doc.put("revocation_endpoint", issuer + "/oauth2/revoke");
        doc.put("response_types_supported", List.of("code", "token"));
        doc.put("grant_types_supported", List.of("password", "refresh_token", "authorization_code"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic"));
        return doc;
    }

    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = jwksConfig.getPublicKey();

        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", "id-platform-key-1");
        key.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()));
        key.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray()));

        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", List.of(key));
        return jwks;
    }

    public Map<String, Object> getUserInfo(String token) {
        Claims claims = tokenService.validateToken(token);
        if (claims == null) {
            return null;
        }

        Long userId = Long.parseLong(claims.getSubject());
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isEmpty() || !userOpt.get().isActive()) {
            return null;
        }

        User user = userOpt.get();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("sub", String.valueOf(user.getId()));
        userInfo.put("email", user.getEmail());
        userInfo.put("name", user.getDisplayName());
        userInfo.put("email_verified", true);
        userInfo.put("source", user.getSource() != null ? user.getSource().name() : "INTERNAL");
        return userInfo;
    }

    public Claims introspectToken(String token) {
        return tokenService.validateToken(token);
    }
}
