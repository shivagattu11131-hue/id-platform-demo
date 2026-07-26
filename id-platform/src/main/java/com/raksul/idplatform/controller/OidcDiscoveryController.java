package com.raksul.idplatform.controller;

import com.raksul.idplatform.service.OidcService;
import com.raksul.idplatform.service.MigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
public class OidcDiscoveryController {

    @Autowired
    private OidcService oidcService;

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private ResourceLoader resourceLoader;

    private String dashboardHtml;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String root() throws IOException {
        if (dashboardHtml == null) {
            var resource = resourceLoader.getResource("classpath:templates/dashboard.html");
            dashboardHtml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        return dashboardHtml;
    }

    @GetMapping("/api/info")
    public ResponseEntity<Map<String, Object>> info() {
        var clientList = oidcService.listClients();
        Map<String, String> clientMap = new java.util.LinkedHashMap<>();
        for (var c : clientList) {
            clientMap.put(c.clientId, c.name);
        }
        return ResponseEntity.ok(Map.of(
            "service", "Raksul ID Platform",
            "version", "1.0.0",
            "status", "running",
            "issuer", "http://localhost:3000",
            "endpoints", Map.of(
                "discovery", "http://localhost:3000/.well-known/openid-configuration",
                "jwks", "http://localhost:3000/jwks.json",
                "authorize", "http://localhost:3000/oauth2/authorize",
                "token", "http://localhost:3000/oauth2/token",
                "userinfo", "http://localhost:3000/oauth2/userinfo",
                "register", "http://localhost:3000/oauth2/register",
                "clients", "http://localhost:3000/oauth2/clients",
                "health", "http://localhost:3000/api/health"
            ),
            "registered_clients", clientMap
        ));
    }

    @GetMapping("/api/migration/status")
    public ResponseEntity<Map<String, Object>> getMigrationStatus() {
        return ResponseEntity.ok(migrationService.getMigrationStatus());
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
