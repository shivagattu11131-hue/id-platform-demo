package com.raksul.idplatform.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "source")
    @Enumerated(EnumType.STRING)
    private UserSource source = UserSource.INTERNAL;

    @Column(name = "legacy_main_site_id")
    private String legacyMainSiteId;

    @Column(name = "legacy_ma_site_id")
    private String legacyMaSiteId;

    @Column(name = "migration_phase")
    private String migrationPhase;

    @Column(name = "conflict_flagged")
    private boolean conflictFlagged = false;

    @Column(name = "conflict_details")
    private String conflictDetails;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public enum UserSource {
        INTERNAL, MAIN_SITE, MA_SITE, MERGED
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public User() {}

    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public UserSource getSource() { return source; }
    public void setSource(UserSource source) { this.source = source; }

    public String getLegacyMainSiteId() { return legacyMainSiteId; }
    public void setLegacyMainSiteId(String id) { this.legacyMainSiteId = id; }

    public String getLegacyMaSiteId() { return legacyMaSiteId; }
    public void setLegacyMaSiteId(String id) { this.legacyMaSiteId = id; }

    public String getMigrationPhase() { return migrationPhase; }
    public void setMigrationPhase(String phase) { this.migrationPhase = phase; }

    public boolean isConflictFlagged() { return conflictFlagged; }
    public void setConflictFlagged(boolean flagged) { this.conflictFlagged = flagged; }

    public String getConflictDetails() { return conflictDetails; }
    public void setConflictDetails(String details) { this.conflictDetails = details; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
