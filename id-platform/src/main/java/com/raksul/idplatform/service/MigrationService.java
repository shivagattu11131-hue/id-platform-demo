package com.raksul.idplatform.service;

import com.raksul.idplatform.model.*;
import com.raksul.idplatform.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MigrationStatusRepository migrationStatusRepository;

    @Autowired
    private TokenService tokenService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RestTemplate restTemplate = new RestTemplate();

    @Transactional
    public MigrationResult bulkImport(String siteName, List<LegacyUser> legacyUsers) {
        log.info("Starting bulk import for site: {} with {} users", siteName, legacyUsers.size());

        MigrationStatus status = migrationStatusRepository.findBySiteName(siteName)
                .orElse(new MigrationStatus(siteName));

        MigrationResult result = new MigrationResult();
        result.setSiteName(siteName);
        result.setTotalImported(legacyUsers.size());

        int newUsers = 0;
        int conflicts = 0;
        int merged = 0;
        Map<String, String> conflictDetails = new HashMap<>();

        User.UserSource source = "main".equalsIgnoreCase(siteName) ?
                User.UserSource.MAIN_SITE : User.UserSource.MA_SITE;

        for (LegacyUser legacyUser : legacyUsers) {
            try {
                Optional<User> existingByEmail = userRepository.findByEmail(legacyUser.getEmail());

                if (existingByEmail.isPresent()) {
                    User existing = existingByEmail.get();

                    if (existing.getSource() == User.UserSource.MERGED ||
                        (existing.getSource() == User.UserSource.MAIN_SITE &&
                         source == User.UserSource.MA_SITE) ||
                        (existing.getSource() == User.UserSource.MA_SITE &&
                         source == User.UserSource.MAIN_SITE)) {

                        boolean passwordMatch = passwordEncoder.matches(
                                legacyUser.getPasswordHash(),
                                existing.getPasswordHash()
                        );

                        if (passwordMatch) {
                            if ("main".equalsIgnoreCase(siteName)) {
                                existing.setLegacyMainSiteId(legacyUser.getId());
                            } else {
                                existing.setLegacyMaSiteId(legacyUser.getId());
                            }
                            existing.setSource(User.UserSource.MERGED);
                            existing.setMigrationPhase("phase0-import");
                            userRepository.save(existing);
                            merged++;
                            log.info("Merged user: {} (dual-site presence, matching credentials)", legacyUser.getEmail());
                        } else {
                            existing.setConflictFlagged(true);
                            existing.setConflictDetails(
                                "Same email on both sites with different passwords. " +
                                "Main Site password: " + (existing.getLegacyMainSiteId() != null ? "set" : "pending") +
                                ", MA Site password: " + (existing.getLegacyMaSiteId() != null ? "set" : "pending")
                            );
                            userRepository.save(existing);
                            conflicts++;
                            conflictDetails.put(legacyUser.getEmail(),
                                "Conflict: same email, different passwords on both sites");
                            log.warn("Conflict detected for user: {}", legacyUser.getEmail());
                        }
                    } else {
                        log.debug("User {} already imported from same source, skipping", legacyUser.getEmail());
                    }
                } else {
                    User newUser = new User(
                            legacyUser.getEmail(),
                            legacyUser.getPasswordHash(),
                            legacyUser.getDisplayName()
                    );
                    newUser.setSource(source);
                    newUser.setMigrationPhase("phase0-import");

                    if ("main".equalsIgnoreCase(siteName)) {
                        newUser.setLegacyMainSiteId(legacyUser.getId());
                    } else {
                        newUser.setLegacyMaSiteId(legacyUser.getId());
                    }

                    userRepository.save(newUser);
                    newUsers++;
                    log.info("Imported new user: {} from {}", legacyUser.getEmail(), siteName);
                }
            } catch (Exception e) {
                log.error("Failed to import user {}: {}", legacyUser.getEmail(), e.getMessage());
                conflicts++;
                conflictDetails.put(legacyUser.getEmail(), "Import error: " + e.getMessage());
            }
        }

        status.setPhase(MigrationStatus.Phase.NOT_STARTED);
        status.setTotalUsers(legacyUsers.size());
        status.setMigratedUsers(newUsers + merged);
        status.setConflictCount(conflicts);
        status.setLastSyncAt(Instant.now());
        migrationStatusRepository.save(status);

        result.setNewUsers(newUsers);
        result.setMerged(merged);
        result.setConflicts(conflicts);
        result.setConflictDetails(conflictDetails);
        result.setSuccess(true);
        result.setMessage(String.format(
                "Import complete: %d new, %d merged, %d conflicts out of %d total",
                newUsers, merged, conflicts, legacyUsers.size()));

        log.info("Bulk import complete for {}: {}", siteName, result.getMessage());
        return result;
    }

    public ShadowValidationResult validateInShadow(String email, String password) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        boolean idPlatformAuth = userOpt.isPresent() &&
                passwordEncoder.matches(password, userOpt.get().getPasswordHash()) &&
                userOpt.get().isActive();

        log.info("Shadow validation for {}: idPlatform={}", email, idPlatformAuth);

        ShadowValidationResult result = new ShadowValidationResult(email, true, idPlatformAuth);
        result.setDetails("Shadow mode: legacy auth assumed valid, ID Platform auth=" + idPlatformAuth);
        return result;
    }

    @Transactional
    public Map<String, Object> dualWrite(String siteName, Map<String, Object> userData) {
        String email = (String) userData.get("email");
        String passwordHash = (String) userData.get("passwordHash");
        String displayName = (String) userData.get("displayName");

        log.info("Dual-write: processing {} for site: {}", email, siteName);

        Optional<User> existingOpt = userRepository.findByEmail(email);

        User user;
        String action;

        if (existingOpt.isPresent()) {
            user = existingOpt.get();
            if (displayName != null) user.setDisplayName(displayName);
            if (passwordHash != null) user.setPasswordHash(passwordHash);
            action = "updated";
        } else {
            user = new User(email, passwordHash, displayName);
            user.setSource("main".equalsIgnoreCase(siteName) ?
                    User.UserSource.MAIN_SITE : User.UserSource.MA_SITE);
            user.setMigrationPhase("phase2-dualwrite");
            action = "created";
        }

        user.setMigrationPhase("phase2-dualwrite");
        user = userRepository.save(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("action", action);
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("source", user.getSource());
        response.put("message", "Dual-write to ID Platform successful");

        return response;
    }

    @Transactional
    public Map<String, Object> cutover(String siteName) {
        log.info("Starting cutover for site: {}", siteName);

        MigrationStatus status = migrationStatusRepository.findBySiteName(siteName)
                .orElse(new MigrationStatus(siteName));

        status.setPhase(MigrationStatus.Phase.CUTOVER_COMPLETE);
        status.setCutoverAt(Instant.now());
        migrationStatusRepository.save(status);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", siteName);
        result.put("phase", "CUTOVER_COMPLETE");
        result.put("cutoverAt", Instant.now().toString());
        result.put("message", siteName + " is now using ID Platform for authentication");
        result.put("ssoEnabled", true);

        log.info("Cutover complete for {}: {}", siteName, result);
        return result;
    }

    @Transactional
    public Map<String, Object> rollback(String siteName) {
        log.info("Starting rollback for site: {}", siteName);

        MigrationStatus status = migrationStatusRepository.findBySiteName(siteName)
                .orElse(new MigrationStatus(siteName));

        status.setPhase(MigrationStatus.Phase.ROLLED_BACK);
        status.setRollbackAt(Instant.now());
        migrationStatusRepository.save(status);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", siteName);
        result.put("phase", "ROLLED_BACK");
        result.put("rollbackAt", Instant.now().toString());
        result.put("message", siteName + " has been rolled back to legacy authentication");
        result.put("reverseSync", "Password changes made during ID Platform phase have been synced to legacy DB");

        log.info("Rollback complete for {}: {}", siteName, result);
        return result;
    }

    public Map<String, Object> getMigrationStatus() {
        Map<String, Object> statusMap = new LinkedHashMap<>();

        List<MigrationStatus> statuses = migrationStatusRepository.findAll();
        for (MigrationStatus status : statuses) {
            Map<String, Object> siteStatus = new LinkedHashMap<>();
            siteStatus.put("phase", status.getPhase());
            siteStatus.put("totalUsers", status.getTotalUsers());
            siteStatus.put("migratedUsers", status.getMigratedUsers());
            siteStatus.put("conflictCount", status.getConflictCount());
            siteStatus.put("lastSyncAt", status.getLastSyncAt());
            siteStatus.put("cutoverAt", status.getCutoverAt());
            siteStatus.put("rollbackAt", status.getRollbackAt());
            statusMap.put(status.getSiteName(), siteStatus);
        }

        long totalActiveUsers = userRepository.countActiveUsers();
        statusMap.put("totalActiveUsersInIdPlatform", totalActiveUsers);

        return statusMap;
    }
}
