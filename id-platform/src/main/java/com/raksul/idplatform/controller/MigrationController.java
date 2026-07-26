package com.raksul.idplatform.controller;

import com.raksul.idplatform.model.LegacyUser;
import com.raksul.idplatform.model.MigrationResult;
import com.raksul.idplatform.model.ShadowValidationResult;
import com.raksul.idplatform.service.MigrationService;
import com.raksul.idplatform.service.MigrationDemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/migration")
@CrossOrigin(origins = "*")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    @Autowired
    private MigrationService migrationService;

    @Autowired
    private MigrationDemoService migrationDemoService;

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

    @PostMapping("/run-phase/{phase}")
    public ResponseEntity<Map<String, Object>> runPhase(@PathVariable int phase) {
        Map<String, Object> result = migrationDemoService.runPhase(phase);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/run-demo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter runDemo() {
        SseEmitter emitter = new SseEmitter(300000L);
        emitter.onCompletion(() -> log.info("SSE demo completed"));
        emitter.onTimeout(() -> log.warn("SSE demo timed out"));
        emitter.onError(e -> log.warn("SSE demo error: {}", e.getMessage()));

        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                migrationDemoService.runFullDemoStreaming(phase -> {
                    try {
                        emitter.send(SseEmitter.event()
                            .name("phase")
                            .data(phase, MediaType.APPLICATION_JSON));
                    } catch (Exception e) {
                        log.debug("Failed to send phase event (client likely disconnected): {}", e.getMessage());
                    }
                });

                try {
                    Map<String, Object> summary = Map.of(
                        "durationMs", System.currentTimeMillis() - startTime,
                        "totalPhases", 5,
                        "completed", true
                    );
                    emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(summary, MediaType.APPLICATION_JSON));
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("Failed to send complete event: {}", e.getMessage());
                }
            } catch (Exception e) {
                try { emitter.completeWithError(e); } catch (Exception ignored) {}
            }
        }).start();

        return emitter;
    }
}
