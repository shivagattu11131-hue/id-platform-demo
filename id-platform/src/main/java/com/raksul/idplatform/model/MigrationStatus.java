package com.raksul.idplatform.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "migration_status")
public class MigrationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false, unique = true)
    private String siteName;

    @Column(name = "phase", nullable = false)
    @Enumerated(EnumType.STRING)
    private Phase phase = Phase.NOT_STARTED;

    @Column(name = "total_users")
    private int totalUsers = 0;

    @Column(name = "migrated_users")
    private int migratedUsers = 0;

    @Column(name = "conflict_count")
    private int conflictCount = 0;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "cutover_at")
    private Instant cutoverAt;

    @Column(name = "rollback_at")
    private Instant rollbackAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public enum Phase {
        NOT_STARTED,
        SHADOW_MODE,
        DUAL_WRITE,
        CUTOVER_COMPLETE,
        ROLLED_BACK
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public MigrationStatus() {}

    public MigrationStatus(String siteName) {
        this.siteName = siteName;
    }

    public Long getId() { return id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String name) { this.siteName = name; }

    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public int getTotalUsers() { return totalUsers; }
    public void setTotalUsers(int total) { this.totalUsers = total; }

    public int getMigratedUsers() { return migratedUsers; }
    public void setMigratedUsers(int migrated) { this.migratedUsers = migrated; }

    public int getConflictCount() { return conflictCount; }
    public void setConflictCount(int count) { this.conflictCount = count; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant t) { this.lastSyncAt = t; }

    public Instant getCutoverAt() { return cutoverAt; }
    public void setCutoverAt(Instant t) { this.cutoverAt = t; }

    public Instant getRollbackAt() { return rollbackAt; }
    public void setRollbackAt(Instant t) { this.rollbackAt = t; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
