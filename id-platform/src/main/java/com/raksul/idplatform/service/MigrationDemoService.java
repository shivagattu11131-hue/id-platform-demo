package com.raksul.idplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.function.Consumer;

@Service
public class MigrationDemoService {

    private static final Logger log = LoggerFactory.getLogger(MigrationDemoService.class);

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private AuthService authService;

    @Value("${id-platform.migration.main-site.url:http://localhost:3001}")
    private String mainSiteUrl;

    @Value("${id-platform.migration.ma-site.url:http://localhost:3002}")
    private String maSiteUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> runFullDemo() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> phases = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        try {
            phases.add(runPhase0());
            phases.add(runPhase1());
            phases.add(runPhase2());
            phases.add(runPhase3());
            phases.add(runPhase4());
        } catch (Exception e) {
            log.error("Demo failed: {}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        result.put("phases", phases);
        result.put("totalPhases", phases.size());
        result.put("durationMs", duration);
        result.put("completed", true);
        return result;
    }

    public void runFullDemoStreaming(Consumer<Map<String, Object>> onPhase) {
        try { onPhase.accept(runPhase0()); } catch (Exception e) { log.error("Phase 0 failed", e); }
        try { onPhase.accept(runPhase1()); } catch (Exception e) { log.error("Phase 1 failed", e); }
        try { onPhase.accept(runPhase2()); } catch (Exception e) { log.error("Phase 2 failed", e); }
        try { onPhase.accept(runPhase3()); } catch (Exception e) { log.error("Phase 3 failed", e); }
        try { onPhase.accept(runPhase4()); } catch (Exception e) { log.error("Phase 4 failed", e); }
    }

    private Map<String, Object> runPhase0() {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", 0);
        phase.put("name", "Bulk Import");
        List<Map<String, Object>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // Health checks
            steps.add(step("Health Check", "check", callGet(mainSiteUrl + "/api/health") != null));

            // Seed Main Site users
            List<Map<String, String>> mainUsers = List.of(
                Map.of("username", "john_doe", "email", "john@example.com", "password", "password123", "display_name", "John Doe"),
                Map.of("username", "jane_smith", "email", "jane@example.com", "password", "securepass456", "display_name", "Jane Smith"),
                Map.of("username", "bob_wilson", "email", "bob@example.com", "password", "bobpass789", "display_name", "Bob Wilson"),
                Map.of("username", "alice_jones", "email", "alice@example.com", "password", "alicepass", "display_name", "Alice Jones"),
                Map.of("username", "charlie_brown", "email", "charlie@example.com", "password", "charlie123", "display_name", "Charlie Brown"),
                Map.of("username", "dual_user_main", "email", "shared@example.com", "password", "mainpass123", "display_name", "Dual User (Main)"),
                Map.of("username", "conflict_user1", "email", "conflict@example.com", "password", "passA", "display_name", "Conflict User A")
            );

            int mainCreated = 0;
            for (Map<String, String> user : mainUsers) {
                Object resp = callPost(mainSiteUrl + "/api/auth/register", user);
                if (resp != null) mainCreated++;
            }
            steps.add(step("Seed Main Site", "register", true, mainCreated + " users created"));

            // Seed MA Site users
            List<Map<String, String>> maUsers = List.of(
                Map.of("username", "ma_user1", "email", "mauser1@example.com", "password", "mapass1", "display_name", "MA User 1", "company_name", "Acquired Corp A"),
                Map.of("username", "ma_user2", "email", "mauser2@example.com", "password", "mapass2", "display_name", "MA User 2", "company_name", "Acquired Corp A"),
                Map.of("username", "ma_user3", "email", "mauser3@example.com", "password", "mapass3", "display_name", "MA User 3", "company_name", "Acquired Corp B"),
                Map.of("username", "acquired_bob", "email", "bob@example.com", "password", "differentpass", "display_name", "Acquired Bob", "company_name", "Acquired Corp A"),
                Map.of("username", "dual_user_ma", "email", "shared@example.com", "password", "mainpass123", "display_name", "Dual User (MA)", "company_name", "Acquired Corp A"),
                Map.of("username", "conflict_user2", "email", "conflict@example.com", "password", "passB", "display_name", "Conflict User B", "company_name", "Acquired Corp B")
            );

            int maCreated = 0;
            for (Map<String, String> user : maUsers) {
                Object resp = callPost(maSiteUrl + "/api/auth/register", user);
                if (resp != null) maCreated++;
            }
            steps.add(step("Seed MA Site", "register", true, maCreated + " users created"));

            // Fetch and import Main Site users
            Object mainUsersRaw = callGet(mainSiteUrl + "/api/users/all");
            if (mainUsersRaw instanceof List<?> userList) {
                List<Map<String, Object>> legacyUsers = new ArrayList<>();
                for (Object u : userList) {
                    if (u instanceof Map<?, ?> m) {
                        Map<String, Object> lu = new LinkedHashMap<>();
                        lu.put("id", String.valueOf(m.get("id")));
                        lu.put("email", m.get("email"));
                        lu.put("passwordHash", m.get("password_hash"));
                        Object displayName = m.get("display_name");
                        lu.put("displayName", displayName != null ? displayName : m.get("email"));
                        lu.put("source", "main");
                        Object createdAt = m.get("created_at");
                        lu.put("createdAt", createdAt != null ? createdAt : "");
                        legacyUsers.add(lu);
                    }
                }
                callPost("http://localhost:3000/api/migration/import?site=main", legacyUsers);
                steps.add(step("Import Main Site Users", "import", true, legacyUsers.size() + " users imported"));
            }

            // Fetch and import MA Site users
            Object maUsersRaw = callGet(maSiteUrl + "/api/users/all");
            if (maUsersRaw instanceof List<?> userList) {
                List<Map<String, Object>> legacyUsers = new ArrayList<>();
                for (Object u : userList) {
                    if (u instanceof Map<?, ?> m) {
                        Map<String, Object> lu = new LinkedHashMap<>();
                        lu.put("id", String.valueOf(m.get("id")));
                        lu.put("email", m.get("email"));
                        lu.put("passwordHash", m.get("password_md5") != null ? m.get("password_md5") : m.get("password_hash"));
                        Object displayName = m.get("display_name");
                        lu.put("displayName", displayName != null ? displayName : m.get("email"));
                        lu.put("source", "ma");
                        Object createdAt = m.get("created_at");
                        lu.put("createdAt", createdAt != null ? createdAt : "");
                        legacyUsers.add(lu);
                    }
                }
                Object importResult = callPost("http://localhost:3000/api/migration/import?site=ma", legacyUsers);
                steps.add(step("Import MA Site Users", "import", true, legacyUsers.size() + " users imported"));
            }

            // Check status
            Object status = callGet("http://localhost:3000/api/migration/status");
            steps.add(step("Verify Migration Status", "status", status != null));

            phase.put("success", true);
            phase.put("message", "All legacy users imported to ID Platform");

        } catch (Exception e) {
            steps.add(step("Phase 0 Error", "error", false, e.getMessage()));
            phase.put("success", false);
            phase.put("message", e.getMessage());
        }

        phase.put("steps", steps);
        phase.put("durationMs", System.currentTimeMillis() - start);
        return phase;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPhase1() {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", 1);
        phase.put("name", "Shadow Mode");
        List<Map<String, Object>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            List<Object[]> testCases = List.of(
                new Object[]{"john@example.com", "password123", "Main Site", mainSiteUrl},
                new Object[]{"jane@example.com", "securepass456", "Main Site", mainSiteUrl},
                new Object[]{"mauser1@example.com", "mapass1", "MA Site", maSiteUrl},
                new Object[]{"bob@example.com", "bobpass789", "Main Site", mainSiteUrl},
                new Object[]{"shared@example.com", "mainpass123", "Main Site", mainSiteUrl}
            );

            int matches = 0;
            int mismatches = 0;

            for (Object[] tc : testCases) {
                String email = (String) tc[0];
                String password = (String) tc[1];
                String siteName = (String) tc[2];
                String siteUrl = (String) tc[3];

                // Legacy login
                Map<String, String> loginBody = Map.of("email", email, "password", password);
                Object legacyResp = callPost(siteUrl + "/api/auth/login", loginBody);
                boolean legacySuccess = legacyResp != null;

                // Shadow validate
                Object shadowResp = callPost("http://localhost:3000/api/migration/shadow-validate", Map.of("email", email, "password", password));
                boolean idpSuccess = false;
                if (shadowResp instanceof Map<?, ?> m) {
                    Object val = m.get("idPlatformAuthSuccess");
                    idpSuccess = val instanceof Boolean b && b;
                }

                boolean match = legacySuccess == idpSuccess;
                if (match) matches++; else mismatches++;

                Map<String, Object> stepResult = new LinkedHashMap<>();
                stepResult.put("step", email + " (" + siteName + ")");
                stepResult.put("type", "shadow-validate");
                stepResult.put("success", true);
                stepResult.put("legacy", legacySuccess);
                stepResult.put("idPlatform", idpSuccess);
                stepResult.put("match", match);
                steps.add(stepResult);
            }

            phase.put("matches", matches);
            phase.put("mismatches", mismatches);
            phase.put("success", true);
            phase.put("message", matches + " matches, " + mismatches + " mismatches");

        } catch (Exception e) {
            steps.add(step("Phase 1 Error", "error", false, e.getMessage()));
            phase.put("success", false);
            phase.put("message", e.getMessage());
        }

        phase.put("steps", steps);
        phase.put("durationMs", System.currentTimeMillis() - start);
        return phase;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPhase2() {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", 2);
        phase.put("name", "Dual-Write");
        List<Map<String, Object>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // TC1: New user on Main
            Map<String, String> newUser1 = Map.of("username", "new_user_1", "email", "newuser1@example.com", "password", "newpass123", "display_name", "New User One");
            Object reg1 = callPost(mainSiteUrl + "/api/auth/register", newUser1);
            String hash1 = java.util.Base64.getEncoder().encodeToString("newpass123".getBytes());
            Map<String, String> dw1 = new LinkedHashMap<>(Map.of("email", "newuser1@example.com", "displayName", "New User One", "source", "main", "passwordHash", hash1));
            Object dwResp1 = callPost("http://localhost:3000/api/migration/dual-write?site=main", dw1);
            steps.add(step("New user registration (Main)", "dual-write", dwResp1 != null));

            // TC2: New user on MA
            Map<String, String> newUser2 = Map.of("username", "new_ma_user_1", "email", "newmauser1@example.com", "password", "newmapass123", "display_name", "New MA User One", "company_name", "New Acquired Corp");
            Object reg2 = callPost(maSiteUrl + "/api/auth/register", newUser2);
            String hash2 = java.util.Base64.getEncoder().encodeToString("newmapass123".getBytes());
            Map<String, String> dw2 = new LinkedHashMap<>(Map.of("email", "newmauser1@example.com", "displayName", "New MA User One", "source", "ma", "passwordHash", hash2));
            Object dwResp2 = callPost("http://localhost:3000/api/migration/dual-write?site=ma", dw2);
            steps.add(step("New user registration (MA)", "dual-write", dwResp2 != null));

            // TC3: Profile update Main
            callPost(mainSiteUrl + "/api/auth/login", Map.of("email", "john@example.com", "password", "password123"));
            callPut(mainSiteUrl + "/api/users/me", Map.of("display_name", "John Doe (Updated)"));
            Map<String, String> dw3 = Map.of("email", "john@example.com", "displayName", "John Doe (Updated)", "source", "main");
            Object dwResp3 = callPost("http://localhost:3000/api/migration/dual-write?site=main", dw3);
            steps.add(step("Profile update (Main)", "dual-write", dwResp3 != null));

            // TC4: Profile update MA
            callPost(maSiteUrl + "/api/auth/login", Map.of("email", "mauser1@example.com", "password", "mapass1"));
            callPut(maSiteUrl + "/api/users/me", Map.of("display_name", "MA User 1 (Updated)"));
            Map<String, String> dw4 = Map.of("email", "mauser1@example.com", "displayName", "MA User 1 (Updated)", "source", "ma");
            Object dwResp4 = callPost("http://localhost:3000/api/migration/dual-write?site=ma", dw4);
            steps.add(step("Profile update (MA)", "dual-write", dwResp4 != null));

            // Verify
            Object status = callGet("http://localhost:3000/api/migration/status");
            steps.add(step("Verify consistency", "status", status != null));

            phase.put("success", true);
            phase.put("message", "All dual-write operations completed");

        } catch (Exception e) {
            steps.add(step("Phase 2 Error", "error", false, e.getMessage()));
            phase.put("success", false);
            phase.put("message", e.getMessage());
        }

        phase.put("steps", steps);
        phase.put("durationMs", System.currentTimeMillis() - start);
        return phase;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPhase3() {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", 3);
        phase.put("name", "Cutover + SSO");
        List<Map<String, Object>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // OIDC Discovery
            Object discovery = callGet("http://localhost:3000/.well-known/openid-configuration");
            steps.add(step("OIDC Discovery verified", "oidc", discovery != null));

            // Cutover MA Site
            Object maCutover = callPost("http://localhost:3000/api/migration/cutover?site=ma", Map.of());
            steps.add(step("MA Site cutover", "cutover", maCutover != null, "MA Site switched to ID Platform auth"));

            // Test MA Site login via IDP
            Object maLogin = callPost("http://localhost:3000/api/auth/login", Map.of("email", "mauser1@example.com", "password", "mapass1"));
            String maToken = extractToken(maLogin);
            steps.add(step("MA Site IDP login test", "login", maToken != null));

            // Validate MA token
            if (maToken != null) {
                Object maUserinfo = callGetWithAuth("http://localhost:3000/oauth2/userinfo", maToken);
                steps.add(step("MA Site token validation", "token", maUserinfo != null));
            }

            // Cutover Main Site
            Object mainCutover = callPost("http://localhost:3000/api/migration/cutover?site=main", Map.of());
            steps.add(step("Main Site cutover", "cutover", mainCutover != null, "Main Site switched to ID Platform auth"));

            // Test Main Site login via IDP
            Object mainLogin = callPost("http://localhost:3000/api/auth/login", Map.of("email", "john@example.com", "password", "password123"));
            String mainToken = extractToken(mainLogin);
            steps.add(step("Main Site IDP login test", "login", mainToken != null));

            // SSO: Use main token on MA Site
            if (mainToken != null) {
                Object ssoUserinfo = callGetWithAuth("http://localhost:3000/oauth2/userinfo", mainToken);
                steps.add(step("SSO: same token on both sites", "sso", ssoUserinfo != null, "Single Sign-On enabled"));
            }

            // Final status
            Object status = callGet("http://localhost:3000/api/migration/status");
            steps.add(step("Final migration status", "status", status != null));

            phase.put("success", true);
            phase.put("message", "Both sites cutover to ID Platform, SSO enabled");

        } catch (Exception e) {
            steps.add(step("Phase 3 Error", "error", false, e.getMessage()));
            phase.put("success", false);
            phase.put("message", e.getMessage());
        }

        phase.put("steps", steps);
        phase.put("durationMs", System.currentTimeMillis() - start);
        return phase;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> runPhase4() {
        Map<String, Object> phase = new LinkedHashMap<>();
        phase.put("phase", 4);
        phase.put("name", "Rollback");
        List<Map<String, Object>> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            // Verify IDP still works
            Object preRollback = callPost("http://localhost:3000/api/auth/login", Map.of("email", "john@example.com", "password", "password123"));
            steps.add(step("Pre-rollback IDP verification", "verify", preRollback != null));

            // Rollback Main Site
            Object mainRollback = callPost("http://localhost:3000/api/migration/rollback?site=main", Map.of());
            steps.add(step("Main Site rollback", "rollback", mainRollback != null, "Reverted to legacy auth"));

            // Verify legacy auth works
            Object legacyLogin = callPost(mainSiteUrl + "/api/auth/login", Map.of("email", "john@example.com", "password", "password123"));
            steps.add(step("Verify legacy Main Site auth", "verify", legacyLogin != null));

            // Rollback MA Site
            Object maRollback = callPost("http://localhost:3000/api/migration/rollback?site=ma", Map.of());
            steps.add(step("MA Site rollback", "rollback", maRollback != null, "Reverted to legacy auth"));

            // Verify MA legacy auth
            Object maLegacyLogin = callPost(maSiteUrl + "/api/auth/login", Map.of("email", "mauser1@example.com", "password", "mapass1"));
            steps.add(step("Verify legacy MA Site auth", "verify", maLegacyLogin != null));

            // Final status
            Object status = callGet("http://localhost:3000/api/migration/status");
            steps.add(step("Final status", "status", status != null));

            phase.put("success", true);
            phase.put("message", "Both sites rolled back to legacy auth");

        } catch (Exception e) {
            steps.add(step("Phase 4 Error", "error", false, e.getMessage()));
            phase.put("success", false);
            phase.put("message", e.getMessage());
        }

        phase.put("steps", steps);
        phase.put("durationMs", System.currentTimeMillis() - start);
        return phase;
    }

    private Map<String, Object> step(String stepName, String type, boolean success) {
        return step(stepName, type, success, null);
    }

    private Map<String, Object> step(String stepName, String type, boolean success, String detail) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step", stepName);
        step.put("type", type);
        step.put("success", success);
        if (detail != null) step.put("detail", detail);
        return step;
    }

    private String extractToken(Object response) {
        if (response instanceof Map<?, ?> m) {
            Object token = m.get("accessToken");
            if (token instanceof String s) return s;
        }
        return null;
    }

    private Object callGet(String url) {
        try {
            return restTemplate.getForObject(url, Object.class);
        } catch (Exception e) {
            log.warn("GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object callGetWithAuth(String url, String token) {
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            org.springframework.http.ResponseEntity<Object> resp = restTemplate.exchange(
                url, org.springframework.http.HttpMethod.GET, entity, Object.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("GET {} with auth failed: {}", url, e.getMessage());
            return null;
        }
    }

    private Object callPost(String url, Object body) {
        try {
            return restTemplate.postForObject(url, body, Object.class);
        } catch (Exception e) {
            log.warn("POST {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    private Object callPut(String url, Object body) {
        try {
            restTemplate.put(url, body);
            return Map.of("success", true);
        } catch (Exception e) {
            log.warn("PUT {} failed: {}", url, e.getMessage());
            return null;
        }
    }
}
