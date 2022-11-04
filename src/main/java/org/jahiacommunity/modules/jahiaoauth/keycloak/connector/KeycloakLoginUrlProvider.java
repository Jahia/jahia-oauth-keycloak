package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.jahia.exceptions.JahiaException;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.params.valves.LoginUrlProvider;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

@Component(service = LoginUrlProvider.class)
public class KeycloakLoginUrlProvider implements LoginUrlProvider {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakLoginUrlProvider.class);

    private SettingsService settingsService;
    private KeycloakConnector keycloakConnector;
    private JahiaSitesService jahiaSitesService;

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Reference
    private void setKeycloakConnector(KeycloakConnector keycloakConnector) {
        this.keycloakConnector = keycloakConnector;
    }

    @Reference
    private void setJahiaSitesService(JahiaSitesService jahiaSitesService) {
        this.jahiaSitesService = jahiaSitesService;
    }

    private String getSiteKey(HttpServletRequest httpServletRequest) {
        String siteKey;
        try {
            JahiaSite jahiaSite = jahiaSitesService.getSiteByServerName(httpServletRequest.getServerName());
            if (jahiaSite != null) {
                siteKey = jahiaSite.getSiteKey();
            } else {
                siteKey = jahiaSitesService.getDefaultSite().getSiteKey();
            }
        } catch (JahiaException e) {
            siteKey = jahiaSitesService.getDefaultSite().getSiteKey();
        }
        return siteKey;
    }

    private String getAuthorizationUrl(String siteKey) {
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY);
        if (connectorConfig == null) {
            return null;
        }
        return keycloakConnector.getAuthorizationUrl(connectorConfig);
    }

    @Override
    public boolean hasCustomLoginUrl() {
        return true;
    }

    @Override
    public String getLoginUrl(HttpServletRequest httpServletRequest) {
        String authorizationUrl = getAuthorizationUrl(getSiteKey(httpServletRequest));
        if (authorizationUrl == null) {
            return null;
        }

        // save the requestUri in the session
        String originalRequestUri = (String) httpServletRequest.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalRequestUri == null) {
            originalRequestUri = httpServletRequest.getRequestURI();
        }
        httpServletRequest.getSession(false).setAttribute(KeycloakCallbackAction.SESSION_REQUEST_URI, originalRequestUri);
        // redirect to SSO
        return authorizationUrl;
    }
}
