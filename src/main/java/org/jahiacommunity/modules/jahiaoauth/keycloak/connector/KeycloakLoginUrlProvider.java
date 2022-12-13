package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.jahia.exceptions.JahiaException;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
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
    private JahiaOAuthService jahiaOAuthService;
    private JahiaSitesService jahiaSitesService;

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Reference
    private void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    @Reference
    private void setJahiaSitesService(JahiaSitesService jahiaSitesService) {
        this.jahiaSitesService = jahiaSitesService;
    }

    private String getSiteKey(HttpServletRequest httpServletRequest) {
        try {
            JahiaSite jahiaSite = jahiaSitesService.getSiteByServerName(httpServletRequest.getServerName());
            if (jahiaSite != null) {
                return jahiaSite.getSiteKey();
            }
        } catch (JahiaException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("", e);
            }
        }

        JahiaSite site = jahiaSitesService.getDefaultSite();
        if (site == null) {
            return null;
        }
        return site.getSiteKey();
    }

    private String getAuthorizationUrl(String siteKey, String sessionId) {
        if (siteKey == null) {
            return null;
        }
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY);
        if (connectorConfig == null) {
            return null;
        }
        return jahiaOAuthService.getAuthorizationUrl(connectorConfig, sessionId, null);
    }

    @Override
    public boolean hasCustomLoginUrl() {
        return true;
    }

    @Override
    public String getLoginUrl(HttpServletRequest httpServletRequest) {
        String authorizationUrl = getAuthorizationUrl(getSiteKey(httpServletRequest), httpServletRequest.getRequestedSessionId());
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
