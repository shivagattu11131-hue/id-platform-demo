package com.raksul.idplatform.controller;

import com.raksul.idplatform.model.AuthorizationCode;
import com.raksul.idplatform.model.User;
import com.raksul.idplatform.service.AuthService;
import com.raksul.idplatform.service.OidcService;
import com.raksul.idplatform.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/oauth2")
@CrossOrigin(origins = "*")
public class OidcController {

    private static final Logger log = LoggerFactory.getLogger(OidcController.class);

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
            @RequestParam(value = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod) {

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
            RedirectAttributes redirectAttributes) {

        try {
            User user = authService.getUserByEmail(email);

            if (user == null || !user.isActive()) {
                return buildErrorRedirect(redirectUri, state, "access_denied", "Invalid credentials", redirectAttributes);
            }

            if (!authService.validateCredentials(email, password)) {
                return buildErrorRedirect(redirectUri, state, "access_denied", "Invalid credentials", redirectAttributes);
            }

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

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", accessToken);
        response.put("refresh_token", refreshToken);
        response.put("id_token", idToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", tokenService.getAccessTokenExpiry() / 1000);
        response.put("scope", authCode.getScope());

        log.info("Token issued via authorization_code for user {} (client: {})", user.getId(), clientId);
        return ResponseEntity.ok(response);
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
