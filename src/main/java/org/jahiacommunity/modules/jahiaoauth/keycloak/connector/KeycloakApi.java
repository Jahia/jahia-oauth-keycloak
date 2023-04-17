package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import com.github.scribejava.apis.openid.OpenIdJsonTokenExtractor;
import com.github.scribejava.core.builder.api.DefaultApi20;
import com.github.scribejava.core.extractors.TokenExtractor;
import com.github.scribejava.core.model.OAuth2AccessToken;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class KeycloakApi extends DefaultApi20 {

    private static final ConcurrentMap<String, KeycloakApi> INSTANCES = new ConcurrentHashMap<>();

    private final String baseUrlWithRealm;

    private KeycloakApi(String baseUrlWithRealm) {
        this.baseUrlWithRealm = baseUrlWithRealm;
    }

    public static KeycloakApi instance(String baseUrl, String realm) {
        return INSTANCES.computeIfAbsent(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "realms/" + realm, KeycloakApi::new);
    }

    @Override
    public String getAccessTokenEndpoint() {
        return baseUrlWithRealm + "/protocol/openid-connect/token";
    }

    @Override
    protected String getAuthorizationBaseUrl() {
        return baseUrlWithRealm + "/protocol/openid-connect/auth";
    }

    @Override
    public TokenExtractor<OAuth2AccessToken> getAccessTokenExtractor() {
        return OpenIdJsonTokenExtractor.instance();
    }
}
