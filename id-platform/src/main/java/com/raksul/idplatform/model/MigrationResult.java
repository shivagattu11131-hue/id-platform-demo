package com.raksul.idplatform.model;

import java.util.Map;

public class MigrationResult {
    private String siteName;
    private int totalImported;
    private int conflicts;
    private int merged;
    private int newUsers;
    private boolean success;
    private String message;
    private Map<String, String> conflictDetails;

    public MigrationResult() {}

    public String getSiteName() { return siteName; }
    public void setSiteName(String name) { this.siteName = name; }

    public int getTotalImported() { return totalImported; }
    public void setTotalImported(int t) { this.totalImported = t; }

    public int getConflicts() { return conflicts; }
    public void setConflicts(int c) { this.conflicts = c; }

    public int getMerged() { return merged; }
    public void setMerged(int m) { this.merged = m; }

    public int getNewUsers() { return newUsers; }
    public void setNewUsers(int n) { this.newUsers = n; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean s) { this.success = s; }

    public String getMessage() { return message; }
    public void setMessage(String msg) { this.message = msg; }

    public Map<String, String> getConflictDetails() { return conflictDetails; }
    public void setConflictDetails(Map<String, String> d) { this.conflictDetails = d; }
}
