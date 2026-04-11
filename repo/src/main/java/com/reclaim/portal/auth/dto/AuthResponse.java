package com.reclaim.portal.auth.dto;

public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private boolean forcePasswordReset;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, boolean forcePasswordReset) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.forcePasswordReset = forcePasswordReset;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public boolean isForcePasswordReset() {
        return forcePasswordReset;
    }

    public void setForcePasswordReset(boolean forcePasswordReset) {
        this.forcePasswordReset = forcePasswordReset;
    }
}
