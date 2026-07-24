package com.raksul.idplatform.model;

public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;
    private UserInfo user;

    public AuthResponse() {}

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserInfo user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String t) { this.accessToken = t; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String t) { this.refreshToken = t; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String t) { this.tokenType = t; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long e) { this.expiresIn = e; }

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo u) { this.user = u; }

    public static class UserInfo {
        private Long id;
        private String email;
        private String displayName;
        private String source;

        public UserInfo() {}

        public UserInfo(User user) {
            this.id = user.getId();
            this.email = user.getEmail();
            this.displayName = user.getDisplayName();
            this.source = user.getSource() != null ? user.getSource().name() : null;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String name) { this.displayName = name; }

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }
}
