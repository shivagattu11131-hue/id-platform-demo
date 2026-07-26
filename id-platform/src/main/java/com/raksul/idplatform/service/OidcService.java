package com.raksul.idplatform.service;

import com.raksul.idplatform.config.JwksConfig;
import com.raksul.idplatform.model.AuthorizationCode;
import com.raksul.idplatform.model.OidcClient;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.repository.AuthorizationCodeRepository;
import com.raksul.idplatform.repository.OidcClientRepository;
import com.raksul.idplatform.repository.UserRepository;
import com.raksul.idplatform.repository.AuthTokenRepository;
import com.raksul.idplatform.model.AuthToken;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Autowired
    private OidcClientRepository oidcClientRepository;

    @Autowired
    private TokenService tokenService;

    @Value("${id-platform.issuer}")
    private String issuer;

    @Value("${id-platform.oidc.authorization-code-expiry:600000}")
    private long authorizationCodeExpiry;

    private final Map<String, ClientConfig> clients = new ConcurrentHashMap<>();

    @Value("${id-platform.clients.main-site.client-id:main-site}") String mainSiteClientId;
    @Value("${id-platform.clients.main-site.client-secret:main-site-secret}") String mainSiteClientSecret;
    @Value("${id-platform.clients.main-site.redirect-uris:http://localhost:3001/callback}") List<String> mainSiteRedirectUris;
    @Value("${id-platform.clients.main-site.scope:openid profile email}") String mainSiteScope;
    @Value("${id-platform.clients.main-site.name:Main Site}") String mainSiteName;
    @Value("${id-platform.clients.ma-site.client-id:ma-site}") String maSiteClientId;
    @Value("${id-platform.clients.ma-site.client-secret:ma-site-secret}") String maSiteClientSecret;
    @Value("${id-platform.clients.ma-site.redirect-uris:http://localhost:3002/callback}") List<String> maSiteRedirectUris;
    @Value("${id-platform.clients.ma-site.scope:openid profile email}") String maSiteScope;
    @Value("${id-platform.clients.ma-site.name:MA Site}") String maSiteName;

    @PostConstruct
    public void initClients() {
        List<String> mainUris = resolveRedirectUris("MAIN_SITE_REDIRECT_URI", "http://localhost:3001/callback");
        List<String> maUris = resolveRedirectUris("MA_SITE_REDIRECT_URI", "http://localhost:3002/callback");

        seedClient(mainSiteClientId, mainSiteClientSecret, mainUris, mainSiteScope, mainSiteName);
        seedClient(maSiteClientId, maSiteClientSecret, maUris, maSiteScope, maSiteName);

        List<OidcClient> dbClients = oidcClientRepository.findAllByActive(true);
        for (OidcClient dbClient : dbClients) {
            if (!clients.containsKey(dbClient.getClientId())) {
                List<String> uris = Arrays.asList(dbClient.getRedirectUris().split(","));
                clients.put(dbClient.getClientId(), new ClientConfig(
                        dbClient.getClientId(), dbClient.getClientSecret(),
                        uris, dbClient.getScopes(), dbClient.getClientName()));
            }
        }

        log.info("Loaded {} OIDC clients: {}", clients.size(), clients.keySet());
        clients.forEach((id, c) -> log.info("  Client [{}]: redirect_uris={}", id, c.redirectUris));
    }

    private List<String> resolveRedirectUris(String envVar, String defaultUri) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            return Arrays.asList(envValue.split("\\s+"));
        }
        return Arrays.asList(defaultUri);
    }

    private void seedClient(String clientId, String clientSecret, List<String> redirectUris, String scope, String name) {
        if (!oidcClientRepository.existsByClientId(clientId)) {
            String urisStr = String.join(",", redirectUris);
            OidcClient entity = new OidcClient(clientId, clientSecret, urisStr, scope, name);
            oidcClientRepository.save(entity);
        } else {
            oidcClientRepository.findByClientId(clientId).ifPresent(entity -> {
                entity.setRedirectUris(String.join(",", redirectUris));
                oidcClientRepository.save(entity);
            });
        }
        clients.put(clientId, new ClientConfig(clientId, clientSecret, redirectUris, scope, name));
        log.info("Seeded OIDC client [{}]: redirect_uris={}", clientId, redirectUris);
    }

    @Transactional
    public ClientConfig registerClient(String clientId, String clientSecret, List<String> redirectUris, String scopes, String name) {
        if (clients.containsKey(clientId) || oidcClientRepository.existsByClientId(clientId)) {
            return null;
        }

        String urisStr = String.join(",", redirectUris);
        OidcClient entity = new OidcClient(clientId, clientSecret, urisStr, scopes, name);
        oidcClientRepository.save(entity);

        ClientConfig config = new ClientConfig(clientId, clientSecret, redirectUris, scopes, name);
        clients.put(clientId, config);

        log.info("Registered new OIDC client: {} ({})", name, clientId);
        return config;
    }

    public List<ClientConfig> listClients() {
        return new ArrayList<>(clients.values());
    }

    public ClientConfig getClient(String clientId) {
        return clients.get(clientId);
    }

    public boolean validateClient(String clientId, String clientSecret) {
        ClientConfig client = clients.get(clientId);
        if (client == null) {
            return false;
        }
        return client.clientSecret.equals(clientSecret);
    }

    public boolean validateRedirectUri(String clientId, String redirectUri) {
        ClientConfig client = clients.get(clientId);
        if (client == null) {
            return false;
        }
        return client.redirectUris.contains(redirectUri);
    }

    public String generateAuthorizationCode(User user, String clientId, String redirectUri,
                                           String scope, String codeChallenge, String codeChallengeMethod,
                                           String nonce, String state) {
        String code = UUID.randomUUID().toString() + UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusMillis(authorizationCodeExpiry);

        AuthorizationCode authCode = new AuthorizationCode(
                code, user, clientId, redirectUri, scope,
                codeChallenge, codeChallengeMethod, nonce, state, expiresAt
        );
        authorizationCodeRepository.save(authCode);

        log.info("Generated authorization code for user {} (client: {})", user.getId(), clientId);
        return code;
    }

    @Transactional
    public AuthorizationCode validateAuthorizationCode(String code, String clientId, String codeVerifier) {
        Optional<AuthorizationCode> authCodeOpt = authorizationCodeRepository.findByCode(code);

        if (authCodeOpt.isEmpty()) {
            log.warn("Authorization code not found: {}", code);
            return null;
        }

        AuthorizationCode authCode = authCodeOpt.get();

        if (authCode.isUsed()) {
            log.warn("Authorization code already used: {}", code);
            return null;
        }

        if (authCode.isExpired()) {
            log.warn("Authorization code expired: {}", code);
            return null;
        }

        if (!authCode.getClientId().equals(clientId)) {
            log.warn("Authorization code client mismatch: {} vs {}", authCode.getClientId(), clientId);
            return null;
        }

        if (authCode.getCodeChallenge() != null && codeVerifier != null) {
            if (!validateCodeVerifier(codeVerifier, authCode.getCodeChallenge())) {
                log.warn("PKCE validation failed for code: {}", code);
                return null;
            }
        }

        authCode.setUsed(true);
        authorizationCodeRepository.save(authCode);

        return authCode;
    }

    private boolean validateCodeVerifier(String codeVerifier, String codeChallenge) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return computedChallenge.equals(codeChallenge);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return false;
        }
    }

    public Map<String, Object> getDiscoveryDocument() {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("issuer", issuer);
        doc.put("authorization_endpoint", issuer + "/oauth2/authorize");
        doc.put("token_endpoint", issuer + "/oauth2/token");
        doc.put("userinfo_endpoint", issuer + "/oauth2/userinfo");
        doc.put("jwks_uri", issuer + "/jwks.json");
        doc.put("revocation_endpoint", issuer + "/oauth2/revoke");
        doc.put("registration_endpoint", issuer + "/oauth2/register");
        doc.put("response_types_supported", List.of("code"));
        doc.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        doc.put("subject_types_supported", List.of("public"));
        doc.put("id_token_signing_alg_values_supported", List.of("RS256"));
        doc.put("token_endpoint_auth_methods_supported", List.of("none", "client_secret_basic", "client_secret_post"));
        doc.put("code_challenge_methods_supported", List.of("S256", "plain"));
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

    public static class ClientConfig {
        public final String clientId;
        public final String clientSecret;
        public final List<String> redirectUris;
        public final String scope;
        public final String name;

        public ClientConfig(String clientId, String clientSecret, List<String> redirectUris,
                           String scope, String name) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUris = redirectUris;
            this.scope = scope;
            this.name = name;
        }
    }
}
