package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import com.google.common.net.HttpHeaders;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.JahiaAuthConstants;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.params.valves.LoginUrlProvider;
import org.jahia.params.valves.LogoutUrlProvider;
import org.jahia.services.sites.JahiaSitesService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.JahiaHelper;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;

@Component(service = {LoginUrlProvider.class, LogoutUrlProvider.class})
public class KeycloakLoginLogoutUrlProvider implements LoginUrlProvider, LogoutUrlProvider {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakLoginLogoutUrlProvider.class);

    private SettingsService settingsService;
    private JahiaOAuthService jahiaOAuthService;
    private JahiaSitesService jahiaSitesService;
    private JCRTemplate jcrTemplate;

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

    @Reference
    private void setJcrTemplate(JCRTemplate jcrTemplate) {
        this.jcrTemplate = jcrTemplate;
    }

    private String getAuthorizationUrl(String siteKey, String sessionId) {
        if (siteKey == null) {
            return null;
        }
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY);
        if (connectorConfig == null) {
            // fallback to systemsite
            connectorConfig = settingsService.getConnectorConfig(JahiaSitesService.SYSTEM_SITE_KEY, KeycloakConnector.KEY);
            if (connectorConfig == null) {
                // no configuration found
                return null;
            }
        }
        if (!connectorConfig.getBooleanProperty(JahiaAuthConstants.PROPERTY_IS_ENABLED)) {
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
        String authorizationUrl = getAuthorizationUrl(JahiaHelper.getSiteKey(httpServletRequest, jcrTemplate, jahiaSitesService), httpServletRequest.getRequestedSessionId());
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

    @Override
    public boolean hasCustomLogoutUrl() {
        return true;
    }

    @Override
    public String getLogoutUrl(HttpServletRequest httpServletRequest) {
        String siteKey = JahiaHelper.getSiteKey(httpServletRequest, jcrTemplate, jahiaSitesService);
        if (StringUtils.isBlank(siteKey)) {
            return null;
        }
        ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY);
        if (connectorConfig == null || connectorConfig.getProperty("logoutSSO") == null || !Boolean.parseBoolean(connectorConfig.getProperty("logoutSSO"))) {
            return null;
        }
        String authorizationUrl = getAuthorizationUrl(siteKey, httpServletRequest.getRequestedSessionId());
        if (authorizationUrl == null) {
            return null;
        }

        String scheme = httpServletRequest.getHeader(HttpHeaders.X_FORWARDED_PROTO);
        if (scheme == null) {
            scheme = httpServletRequest.getScheme();
        }
        return StringUtils.substringBeforeLast(authorizationUrl, "/") + "/logout?redirect_uri=" +
                scheme + "://" + httpServletRequest.getHeader(HttpHeaders.HOST) +
                httpServletRequest.getContextPath() + "/cms/logout";
    }
}
