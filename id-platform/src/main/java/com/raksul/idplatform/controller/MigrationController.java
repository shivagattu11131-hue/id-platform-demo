package com.raksul.idplatform.controller;

import com.raksul.idplatform.model.LegacyUser;
import com.raksul.idplatform.model.MigrationResult;
import com.raksul.idplatform.model.ShadowValidationResult;
import com.raksul.idplatform.service.MigrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/migration")
@CrossOrigin(origins = "*")
public class MigrationController {

    @Autowired
    private MigrationService migrationService;

    @PostMapping("/import")
    public ResponseEntity<MigrationResult> importUsers(
            @RequestParam String site,
            @RequestBody List<LegacyUser> users) {
        MigrationResult result = migrationService.bulkImport(site, users);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/shadow-validate")
    public ResponseEntity<ShadowValidationResult> shadowValidate(
            @RequestBody Map<String, String> request) {
        ShadowValidationResult result = migrationService.validateInShadow(
                request.get("email"),
                request.get("password")
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dual-write")
    public ResponseEntity<Map<String, Object>> dualWrite(
            @RequestParam String site,
            @RequestBody Map<String, Object> userData) {
        Map<String, Object> result = migrationService.dualWrite(site, userData);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cutover")
    public ResponseEntity<Map<String, Object>> cutover(@RequestParam String site) {
        Map<String, Object> result = migrationService.cutover(site);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rollback")
    public ResponseEntity<Map<String, Object>> rollback(@RequestParam String site) {
        Map<String, Object> result = migrationService.rollback(site);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = migrationService.getMigrationStatus();
        return ResponseEntity.ok(status);
    }
}
