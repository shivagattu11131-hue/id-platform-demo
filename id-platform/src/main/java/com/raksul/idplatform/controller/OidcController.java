package com.raksul.idplatform.controller;

import com.raksul.idplatform.model.AuthorizationCode;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.service.AuthService;
import com.raksul.idplatform.service.OidcService;
import com.raksul.idplatform.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/oauth2")
@CrossOrigin(origins = "*")
public class OidcController {

    private static final Logger log = LoggerFactory.getLogger(OidcController.class);
    private static final String IDP_SESSION_COOKIE = "IDP_SESSION";
    private static final int SESSION_COOKIE_MAX_AGE = 86400; // 24 hours

    @Autowired
    private OidcService oidcService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @GetMapping("/authorize")
    public ModelAndView authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", defaultValue = "openid") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(value = "prompt", defaultValue = "login") String prompt,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (!"code".equals(responseType)) {
            ModelAndView errorView = new ModelAndView("login");
            errorView.addObject("error", "Unsupported response_type: " + responseType);
            return errorView;
        }

        OidcService.ClientConfig client = oidcService.getClient(clientId);
        if (client == null) {
            ModelAndView errorView = new ModelAndView("login");
            errorView.addObject("error", "Unknown client_id: " + clientId);
            return errorView;
        }

        if (!oidcService.validateRedirectUri(clientId, redirectUri)) {
            ModelAndView errorView = new ModelAndView("login");
            errorView.addObject("error", "Invalid redirect_uri");
            return errorView;
        }

        // Handle prompt=none for silent authentication (SSO)
        if ("none".equals(prompt)) {
            String idpSession = getSessionCookie(request);
            if (idpSession != null) {
                // User has active IDP session - auto-generate auth code
                User user = authService.getUserById(Long.parseLong(idpSession));
                if (user != null && user.isActive()) {
                    String authCode = oidcService.generateAuthorizationCode(
                            user, clientId, redirectUri, scope,
                            codeChallenge, codeChallengeMethod, nonce, state
                    );

                    StringBuilder redirectUrl = new StringBuilder(redirectUri);
                    redirectUrl.append("?code=").append(authCode);
                    if (state != null) {
                        redirectUrl.append("&state=").append(encodeValue(state));
                    }

                    log.info("Silent auth: Authorization code issued for user {} (client: {})", user.getId(), clientId);
                    return new ModelAndView("redirect:" + redirectUrl.toString());
                }
            }

            // No valid session - return login_required error
            StringBuilder errorUrl = new StringBuilder(redirectUri);
            errorUrl.append("?error=login_required&error_description=User+must+login");
            if (state != null) {
                errorUrl.append("&state=").append(encodeValue(state));
            }
            return new ModelAndView("redirect:" + errorUrl.toString());
        }

        // Normal login flow - show login page
        ModelAndView modelAndView = new ModelAndView("login");
        modelAndView.addObject("clientName", client.name);
        modelAndView.addObject("clientId", clientId);
        modelAndView.addObject("redirectUri", redirectUri);
        modelAndView.addObject("scope", scope);
        modelAndView.addObject("responseType", responseType);
        modelAndView.addObject("state", state);
        modelAndView.addObject("nonce", nonce);
        modelAndView.addObject("codeChallenge", codeChallenge);
        modelAndView.addObject("codeChallengeMethod", codeChallengeMethod);
        modelAndView.addObject("scopes", scope.split(" "));

        return modelAndView;
    }

    @PostMapping("/authorize")
    public String authorizePost(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", defaultValue = "openid") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            User user = authService.getUserByEmail(email);

            if (user == null || !user.isActive()) {
                return buildErrorRedirect(redirectUri, state, "access_denied", "Invalid credentials", redirectAttributes);
            }

            if (!authService.validateCredentials(email, password)) {
                return buildErrorRedirect(redirectUri, state, "access_denied", "Invalid credentials", redirectAttributes);
            }

            // Set IDP session cookie for SSO
            setSessionCookie(response, String.valueOf(user.getId()));

            String authCode = oidcService.generateAuthorizationCode(
                    user, clientId, redirectUri, scope,
                    codeChallenge, codeChallengeMethod, nonce, state
            );

            StringBuilder redirectUrl = new StringBuilder(redirectUri);
            redirectUrl.append("?code=").append(authCode);
            if (state != null) {
                redirectUrl.append("&state=").append(encodeValue(state));
            }

            log.info("Authorization code issued for user {} (client: {})", user.getId(), clientId);
            return "redirect:" + redirectUrl.toString();

        } catch (Exception e) {
            log.error("Authorization failed", e);
            return buildErrorRedirect(redirectUri, state, "server_error", "Internal server error", redirectAttributes);
        }
    }

    @PostMapping("/authorize-register")
    public String authorizeRegister(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam(value = "scope", defaultValue = "openid") String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam(value = "displayName", required = false) String displayName,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {

        try {
            // Check if user already exists
            if (authService.getUserByEmail(email) != null) {
                // Redirect back to login with error
                StringBuilder loginUrl = new StringBuilder("/oauth2/authorize");
                loginUrl.append("?response_type=").append(encodeValue(responseType));
                loginUrl.append("&client_id=").append(encodeValue(clientId));
                loginUrl.append("&redirect_uri=").append(encodeValue(redirectUri));
                loginUrl.append("&scope=").append(encodeValue(scope));
                if (state != null) loginUrl.append("&state=").append(encodeValue(state));
                if (nonce != null) loginUrl.append("&nonce=").append(encodeValue(nonce));
                if (codeChallenge != null) loginUrl.append("&code_challenge=").append(encodeValue(codeChallenge));
                loginUrl.append("&code_challenge_method=").append(encodeValue(codeChallengeMethod));
                loginUrl.append("&error=").append(encodeValue("Email already registered"));
                return "redirect:" + loginUrl.toString();
            }

            // Register the new user
            com.raksul.idplatform.model.AuthRequest authRequest = new com.raksul.idplatform.model.AuthRequest();
            authRequest.setEmail(email);
            authRequest.setPassword(password);
            authRequest.setDisplayName(displayName != null ? displayName : email);

            authService.register(authRequest);

            // Find the newly registered user and log them in
            User user = authService.getUserByEmail(email);
            if (user == null || !user.isActive()) {
                return buildErrorRedirect(redirectUri, state, "server_error", "Registration failed", redirectAttributes);
            }

            // Set IDP session cookie for SSO
            setSessionCookie(response, String.valueOf(user.getId()));

            String authCode = oidcService.generateAuthorizationCode(
                    user, clientId, redirectUri, scope,
                    codeChallenge, codeChallengeMethod, nonce, state
            );

            StringBuilder redirectUrl = new StringBuilder(redirectUri);
            redirectUrl.append("?code=").append(authCode);
            if (state != null) {
                redirectUrl.append("&state=").append(encodeValue(state));
            }

            log.info("User registered and authorized: {} (client: {})", user.getId(), clientId);
            return "redirect:" + redirectUrl.toString();

        } catch (Exception e) {
            log.error("Registration failed", e);
            return buildErrorRedirect(redirectUri, state, "server_error", "Registration failed: " + e.getMessage(), redirectAttributes);
        }
    }

    @PostMapping("/token")
    @ResponseBody
    public ResponseEntity<?> token(@RequestBody Map<String, String> request) {
        String grantType = request.get("grant_type");

        if ("authorization_code".equals(grantType)) {
            return handleAuthorizationCodeGrant(request);
        } else if ("refresh_token".equals(grantType)) {
            return handleRefreshTokenGrant(request);
        }

        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "unsupported_grant_type");
        error.put("error_description", "Grant type not supported: " + grantType);
        return ResponseEntity.badRequest().body(error);
    }

    private ResponseEntity<?> handleAuthorizationCodeGrant(Map<String, String> request) {
        String code = request.get("code");
        String clientId = request.get("client_id");
        String clientSecret = request.get("client_secret");
        String codeVerifier = request.get("code_verifier");
        String redirectUri = request.get("redirect_uri");

        if (code == null || clientId == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_request");
            error.put("error_description", "code and client_id are required");
            return ResponseEntity.badRequest().body(error);
        }

        OidcService.ClientConfig client = oidcService.getClient(clientId);
        if (client == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_client");
            error.put("error_description", "Unknown client_id");
            return ResponseEntity.badRequest().body(error);
        }

        if (clientSecret != null && !client.clientSecret.equals(clientSecret)) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_client");
            error.put("error_description", "Invalid client_secret");
            return ResponseEntity.badRequest().body(error);
        }

        AuthorizationCode authCode = oidcService.validateAuthorizationCode(code, clientId, codeVerifier);
        if (authCode == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_grant");
            error.put("error_description", "Invalid or expired authorization code");
            return ResponseEntity.badRequest().body(error);
        }

        if (redirectUri != null && !redirectUri.equals(authCode.getRedirectUri())) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_grant");
            error.put("error_description", "redirect_uri mismatch");
            return ResponseEntity.badRequest().body(error);
        }

        User user = authCode.getUser();
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);
        String idToken = tokenService.generateIdToken(user, clientId, authCode.getNonce(), accessToken);

        Map<String, Object> tokenResponse = new LinkedHashMap<>();
        tokenResponse.put("access_token", accessToken);
        tokenResponse.put("refresh_token", refreshToken);
        tokenResponse.put("id_token", idToken);
        tokenResponse.put("token_type", "Bearer");
        tokenResponse.put("expires_in", tokenService.getAccessTokenExpiry() / 1000);
        tokenResponse.put("scope", authCode.getScope());

        log.info("Token issued via authorization_code for user {} (client: {})", user.getId(), clientId);
        return ResponseEntity.ok(tokenResponse);
    }

    private ResponseEntity<?> handleRefreshTokenGrant(Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        String clientId = request.get("client_id");

        if (refreshToken == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_request");
            error.put("error_description", "refresh_token is required");
            return ResponseEntity.badRequest().body(error);
        }

        io.jsonwebtoken.Claims claims = tokenService.validateToken(refreshToken);
        if (claims == null || !"refresh".equals(claims.get("type", String.class))) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_grant");
            error.put("error_description", "Invalid refresh token");
            return ResponseEntity.badRequest().body(error);
        }

        Long userId = Long.parseLong(claims.getSubject());
        User user = authService.getUserById(userId);

        String newAccessToken = tokenService.generateAccessToken(user);
        String idToken = tokenService.generateIdToken(user, clientId, null, newAccessToken);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", newAccessToken);
        response.put("refresh_token", refreshToken);
        response.put("id_token", idToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", tokenService.getAccessTokenExpiry() / 1000);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/userinfo")
    @ResponseBody
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_request");
            error.put("error_description", "Missing Authorization header");
            return ResponseEntity.status(401).body(error);
        }

        String token = authHeader.substring(7);
        Map<String, Object> userInfo = oidcService.getUserInfo(token);

        if (userInfo == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "invalid_token");
            error.put("error_description", "Invalid or expired token");
            return ResponseEntity.status(401).body(error);
        }

        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<?> registerClient(@RequestBody Map<String, Object> request) {
        String clientId = (String) request.get("client_id");
        String clientName = (String) request.get("client_name");
        String redirectUrisRaw = (String) request.get("redirect_uris");
        String scopes = (String) request.getOrDefault("scope", "openid profile email");

        if (clientId == null || clientId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "error_description", "client_id is required"));
        }

        if (redirectUrisRaw == null || redirectUrisRaw.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "error_description", "redirect_uris is required"));
        }

        String clientSecret = UUID.randomUUID().toString();
        List<String> redirectUris = Arrays.asList(redirectUrisRaw.split("\\s+"));

        OidcService.ClientConfig config = oidcService.registerClient(
                clientId, clientSecret, redirectUris, scopes, clientName);

        if (config == null) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "invalid_client_metadata",
                    "error_description", "Client ID already exists"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", clientId);
        response.put("client_secret", clientSecret);
        response.put("client_name", clientName);
        response.put("redirect_uris", redirectUris);
        response.put("scope", scopes);
        response.put("token_endpoint_auth_method", "client_secret_post");
        response.put("grant_types", List.of("authorization_code", "refresh_token"));
        response.put("response_types", List.of("code"));

        log.info("Dynamic client registered: {} ({})", clientName, clientId);
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/clients")
    @ResponseBody
    public ResponseEntity<?> listClients() {
        List<OidcService.ClientConfig> clientList = oidcService.listClients();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (OidcService.ClientConfig c : clientList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("client_id", c.clientId);
            item.put("client_name", c.name);
            item.put("redirect_uris", c.redirectUris);
            item.put("scope", c.scope);
            result.add(item);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/revoke")
    @ResponseBody
    public ResponseEntity<?> revokeToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token != null) {
            tokenService.revokeToken(token);
        }
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Token revoked successfully");
        return ResponseEntity.ok(response);
    }

    private String getSessionCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (IDP_SESSION_COOKIE.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setSessionCookie(HttpServletResponse response, String userId) {
        Cookie cookie = new Cookie(IDP_SESSION_COOKIE, userId);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // Set to true in production (HTTPS)
        cookie.setPath("/");
        cookie.setMaxAge(SESSION_COOKIE_MAX_AGE);
        response.addCookie(cookie);
        // Set SameSite attribute via header (Cookie class doesn't support it directly)
        response.addHeader("Set-Cookie", String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=Lax",
                IDP_SESSION_COOKIE, userId, SESSION_COOKIE_MAX_AGE));
    }

    private String buildErrorRedirect(String redirectUri, String state, String error, String errorDescription, RedirectAttributes redirectAttributes) {
        StringBuilder redirectUrl = new StringBuilder(redirectUri);
        redirectUrl.append("?error=").append(error);
        redirectUrl.append("&error_description=").append(encodeValue(errorDescription));
        if (state != null) {
            redirectUrl.append("&state=").append(encodeValue(state));
        }
        return "redirect:" + redirectUrl.toString();
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
