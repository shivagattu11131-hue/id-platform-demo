package com.raksul.idplatform.controller;

import com.raksul.idplatform.service.OidcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OidcDiscoveryController {

    @Autowired
    private OidcService oidcService;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
            "service", "Raksul ID Platform",
            "version", "1.0.0",
            "issuer", "http://localhost:3000",
            "endpoints", Map.of(
                "discovery", "http://localhost:3000/.well-known/openid-configuration",
                "jwks", "http://localhost:3000/jwks.json",
                "authorize", "http://localhost:3000/oauth2/authorize",
                "token", "http://localhost:3000/oauth2/token",
                "userinfo", "http://localhost:3000/oauth2/userinfo",
                "health", "http://localhost:3000/api/health"
            )
        ));
    }

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> getDiscoveryDocument() {
        return ResponseEntity.ok(oidcService.getDiscoveryDocument());
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<Map<String, Object>> getJwks() {
        return ResponseEntity.ok(oidcService.getJwks());
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "id-platform",
            "version", "1.0.0",
            "issuer", "http://localhost:3000"
        ));
    }
}
