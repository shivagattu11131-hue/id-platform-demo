package com.raksul.idplatform.controller;

import com.raksul.idplatform.model.AuthRequest;
import com.raksul.idplatform.model.AuthResponse;
import com.raksul.idplatform.service.AuthService;
import com.raksul.idplatform.service.OidcService;
import com.raksul.idplatform.service.TokenService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@CrossOrigin(origins = "*")
public class OidcController {

    @Autowired
    private OidcService oidcService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestBody AuthRequest request) {
        try {
            AuthResponse authResponse = authService.login(request);

            Map<String, Object> authCode = new LinkedHashMap<>();
            authCode.put("code", authResponse.getAccessToken());
            authCode.put("state", "authenticated");

            return ResponseEntity.ok(authCode);
        } catch (RuntimeException e) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody Map<String, String> request) {
        String grantType = request.get("grant_type");

        if ("password".equals(grantType)) {
            AuthRequest authRequest = new AuthRequest(
                    request.get("email"),
                    request.get("password")
            );
            try {
                AuthResponse authResponse = authService.login(authRequest);
                return ResponseEntity.ok(authResponse);
            } catch (RuntimeException e) {
                Map<String, String> error = new LinkedHashMap<>();
                error.put("error", e.getMessage());
                return ResponseEntity.badRequest().body(error);
            }
        } else if ("refresh_token".equals(grantType)) {
            String refreshToken = request.get("refresh_token");
            Claims claims = tokenService.validateToken(refreshToken);

            if (claims == null || !"refresh".equals(claims.get("type", String.class))) {
                Map<String, String> error = new LinkedHashMap<>();
                error.put("error", "Invalid refresh token");
                return ResponseEntity.badRequest().body(error);
            }

            Long userId = Long.parseLong(claims.getSubject());
            AuthResponse.UserInfo userInfo = authService.getUserInfo(userId);
            AuthResponse authResponse = new AuthResponse(
                    tokenService.generateAccessToken(authService.getUserById(userId)),
                    refreshToken,
                    tokenService.getAccessTokenExpiry() / 1000,
                    userInfo
            );

            return ResponseEntity.ok(authResponse);
        }

        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Unsupported grant_type: " + grantType);
        return ResponseEntity.badRequest().body(error);
    }

    @GetMapping("/userinfo")
    public ResponseEntity<?> getUserInfo(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "Missing Authorization header");
            return ResponseEntity.status(401).body(error);
        }

        String token = authHeader.substring(7);
        Map<String, Object> userInfo = oidcService.getUserInfo(token);

        if (userInfo == null) {
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "Invalid or expired token");
            return ResponseEntity.status(401).body(error);
        }

        return ResponseEntity.ok(userInfo);
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revokeToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token != null) {
            tokenService.revokeToken(token);
        }
        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Token revoked successfully");
        return ResponseEntity.ok(response);
    }
}
