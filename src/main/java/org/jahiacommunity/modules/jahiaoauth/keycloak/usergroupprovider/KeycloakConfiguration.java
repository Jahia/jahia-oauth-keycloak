package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import java.time.LocalDateTime;

public class KeycloakConfiguration {
    public static final String PROPERTY_TARGET_SITE = "target.site";
    public static final String PROPERTY_BASE_URL = "baseUrl";
    public static final String PROPERTY_REALM = "realm";
    public static final String PROPERTY_CLIENT_ID = "clientId";
    public static final String PROPERTY_CLIENT_SECRET = "clientSecret";
    public static final int REFRESH_EXPIRATION_DURATION = 60;

    private final String targetSite;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime refreshExpirationDateTime;

    public KeycloakConfiguration(String targetSite, String baseUrl, String realm, String clientId, String clientSecret) {
        this.targetSite = targetSite;
        this.baseUrl = baseUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getTargetSite() {
        return targetSite;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getRealm() {
        return realm;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
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

    public void setRefreshExpirationDateTime(LocalDateTime refreshExpirationDateTime) {
        this.refreshExpirationDateTime = refreshExpirationDateTime;
    }

    public boolean isRefreshPossible() {
        if (refreshExpirationDateTime == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(refreshExpirationDateTime);
    }
}
