package com.raksul.idplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrationControllerTests {

    @Autowired
    private TestRestTemplate restTemplate;

    private void importUsers(String site, String email, String passwordHash) {
        List<Map<String, String>> users = List.of(Map.of(
                "id", "1",
                "email", email,
                "passwordHash", passwordHash,
                "displayName", "Test User",
                "source", site
        ));
        restTemplate.postForEntity(
                "/api/migration/import?site=" + site, users, Map.class);
    }

    @Test
    void bulkImportCreatesUsers() {
        List<Map<String, String>> users = List.of(
                Map.of("id", "10", "email", "import1@test.com", "passwordHash", "hash1", "displayName", "Import 1"),
                Map.of("id", "11", "email", "import2@test.com", "passwordHash", "hash2", "displayName", "Import 2")
        );

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/migration/import?site=main", users, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("newUsers")).isEqualTo(2);
    }

    @Test
    void bulkImportSetsPhaseToNotStarted() {
        List<Map<String, String>> users = List.of(
                Map.of("id", "20", "email", "phase@test.com", "passwordHash", "hash", "displayName", "Phase User")
        );
        restTemplate.postForEntity("/api/migration/import?site=main", users, Map.class);

        ResponseEntity<Map> statusResp = restTemplate.getForEntity("/api/migration/status", Map.class);
        assertThat(statusResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = statusResp.getBody();
        assertThat(body).isNotNull();
        Map mainStatus = (Map) body.get("main");
        assertThat(mainStatus).isNotNull();
        assertThat(mainStatus.get("phase")).isEqualTo("NOT_STARTED");
    }

    @Test
    void cutoverSetsPhaseToComplete() {
        importUsers("main", "cut@test.com", "hash");

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/migration/cutover?site=main", null, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("phase")).isEqualTo("CUTOVER_COMPLETE");
        assertThat(body.get("ssoEnabled")).isEqualTo(true);
    }

    @Test
    void rollbackSetsPhaseToRolledBack() {
        importUsers("main", "roll@test.com", "hash");
        restTemplate.postForEntity("/api/migration/cutover?site=main", null, Map.class);

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/migration/rollback?site=main", null, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("phase")).isEqualTo("ROLLED_BACK");
    }

    @Test
    void fullMigrationLifecycle() {
        String site = "ma";

        // Phase 0: Import
        List<Map<String, String>> users = List.of(
                Map.of("id", "30", "email", "lifecycle@test.com", "passwordHash", "hash", "displayName", "Lifecycle User")
        );
        ResponseEntity<Map> importResp = restTemplate.postForEntity(
                "/api/migration/import?site=" + site, users, Map.class);
        assertThat(importResp.getBody().get("success")).isEqualTo(true);

        // Verify NOT_STARTED after import
        ResponseEntity<Map> status1 = restTemplate.getForEntity("/api/migration/status", Map.class);
        assertThat(((Map) status1.getBody().get(site)).get("phase")).isEqualTo("NOT_STARTED");

        // Phase 3: Cutover
        ResponseEntity<Map> cutoverResp = restTemplate.postForEntity(
                "/api/migration/cutover?site=" + site, null, Map.class);
        assertThat(cutoverResp.getBody().get("phase")).isEqualTo("CUTOVER_COMPLETE");

        // Phase 4: Rollback
        ResponseEntity<Map> rollbackResp = restTemplate.postForEntity(
                "/api/migration/rollback?site=" + site, null, Map.class);
        assertThat(rollbackResp.getBody().get("phase")).isEqualTo("ROLLED_BACK");
    }

    @Test
    void dualWriteCreatesNewUser() {
        Map<String, Object> userData = Map.of(
                "email", "dual@test.com",
                "passwordHash", "hash123",
                "displayName", "Dual User"
        );

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/migration/dual-write?site=main", userData, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("action")).isEqualTo("created");
        assertThat(body.get("userId")).isNotNull();
    }

    @Test
    void shadowValidationReturnsResult() {
        importUsers("main", "shadow@test.com", "hash");

        ResponseEntity<Map> resp = restTemplate.postForEntity(
                "/api/migration/shadow-validate",
                Map.of("email", "shadow@test.com", "password", "test"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("email")).isEqualTo("shadow@test.com");
        assertThat(body.get("legacyAuthSuccess")).isEqualTo(true);
    }
}
