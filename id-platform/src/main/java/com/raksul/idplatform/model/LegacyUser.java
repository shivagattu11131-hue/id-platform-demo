package com.raksul.idplatform.model;

import java.util.List;
import java.util.Optional;

public class LegacyUser {
    private String id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String source;
    private String createdAt;

    public LegacyUser() {}

    public LegacyUser(String id, String email, String passwordHash, String displayName, String source) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.source = source;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) { this.displayName = name; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String t) { this.createdAt = t; }
}
