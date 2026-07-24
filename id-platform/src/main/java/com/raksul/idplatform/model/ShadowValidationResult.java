package com.raksul.idplatform.model;

public class ShadowValidationResult {
    private boolean legacyAuthSuccess;
    private boolean idPlatformAuthSuccess;
    private boolean match;
    private String email;
    private String details;

    public ShadowValidationResult() {}

    public ShadowValidationResult(String email, boolean legacySuccess, boolean idPlatformSuccess) {
        this.email = email;
        this.legacyAuthSuccess = legacySuccess;
        this.idPlatformAuthSuccess = idPlatformSuccess;
        this.match = legacySuccess == idPlatformSuccess;
    }

    public boolean isLegacyAuthSuccess() { return legacyAuthSuccess; }
    public void setLegacyAuthSuccess(boolean v) { this.legacyAuthSuccess = v; }

    public boolean isIdPlatformAuthSuccess() { return idPlatformAuthSuccess; }
    public void setIdPlatformAuthSuccess(boolean v) { this.idPlatformAuthSuccess = v; }

    public boolean isMatch() { return match; }
    public void setMatch(boolean m) { this.match = m; }

    public String getEmail() { return email; }
    public void setEmail(String e) { this.email = e; }

    public String getDetails() { return details; }
    public void setDetails(String d) { this.details = d; }
}
