package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import com.github.scribejava.apis.KeycloakApi;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.ConnectorPropertyInfo;
import org.jahia.modules.jahiaauth.service.ConnectorService;
import org.jahia.modules.jahiaauth.service.JahiaAuthConstants;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.modules.jahiaoauth.service.OAuthConnectorService;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component(service = {KeycloakConnector.class, OAuthConnectorService.class, ConnectorService.class}, property = {JahiaAuthConstants.CONNECTOR_SERVICE_NAME + "=" + KeycloakConnector.KEY}, immediate = true)
public class KeycloakConnector implements OAuthConnectorService {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakConnector.class);

    public static final String KEY = "KeycloakApi";
    private static final String REALM = "realm";
    private static final String BASEURL = "baseUrl";
    private static final String PROTECTED_RESOURCE_URL = "%s/realms/%s/protocol/openid-connect/userinfo";
    public static final String SSO_LOGIN = "preferred_username";

    private JahiaOAuthService jahiaOAuthService;
    private JahiaSitesService jahiaSitesService;
    private SettingsService settingsService;

    @Reference
    private void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    @Reference
    private void setJahiaSitesService(JahiaSitesService jahiaSitesService) {
        this.jahiaSitesService = jahiaSitesService;
    }

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Activate
    private void onActivate() {
        jahiaOAuthService.addOAuthDefaultApi20(KEY,
                connectorConfig -> KeycloakApi.instance(connectorConfig.getProperty(BASEURL),
                        connectorConfig.getProperty(REALM)));

        jahiaSitesService.getSitesNames().forEach(siteName -> {
            ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteName, KEY);
            if (connectorConfig != null) {
                try {
                    validateSettings(connectorConfig);
                } catch (IOException e) {
                    logger.error("", e);
                }
            }
        });
    }

    @Deactivate
    private void onDeactivate() {
        jahiaOAuthService.removeOAuthDefaultApi20(KEY);
    }

    @Override
    public String getProtectedResourceUrl(ConnectorConfig connectorConfig) {
        return String.format(PROTECTED_RESOURCE_URL, connectorConfig.getProperty(BASEURL),
                connectorConfig.getProperty(REALM));
    }

    @Override
    public List<ConnectorPropertyInfo> getAvailableProperties() {
        List<ConnectorPropertyInfo> availableProperties = new ArrayList<>();
        availableProperties.add(new ConnectorPropertyInfo("name", "string"));
        availableProperties.add(new ConnectorPropertyInfo("given_name", "string"));
        availableProperties.add(new ConnectorPropertyInfo("family_name", "string"));
        availableProperties.add(new ConnectorPropertyInfo(SSO_LOGIN, "string"));
        availableProperties.add(new ConnectorPropertyInfo("email", "email"));
        return availableProperties;
    }

    /* @Override
    public void validateSettings(ConnectorConfig connectorConfig) {
        if (userProviders.containsKey(connectorConfig.getSiteKey())) {
            userProviders.get(connectorConfig.getSiteKey()).unregister();
        }
        userProviders.put(connectorConfig.getSiteKey(), new KeycloakUserProvider(this, externalUserGroupService,
            keycloakClient, connectorConfig));
    } */
}
