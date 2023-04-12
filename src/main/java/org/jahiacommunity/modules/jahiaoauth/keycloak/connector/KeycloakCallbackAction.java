package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.content.JCRTemplate;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.bin.Render;
import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@Component(service = Action.class)
public class KeycloakCallbackAction extends Action {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakCallbackAction.class);

    private static final String NAME = "keycloakOAuthCallbackAction";
    public static final String SESSION_REQUEST_URI = "my.request_uri";

    private JahiaOAuthService jahiaOAuthService;
    private SettingsService settingsService;
    private JCRTemplate jcrTemplate;
    private JahiaSitesService jahiaSitesService;

    @Reference
    private void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Reference
    private void setJcrTemplate(JCRTemplate jcrTemplate) {
        this.jcrTemplate = jcrTemplate;
    }

    @Reference
    private void setJahiaSitesService(JahiaSitesService jahiaSitesService) {
        this.jahiaSitesService = jahiaSitesService;
    }

    public KeycloakCallbackAction() {
        setName(NAME);
        setRequireAuthenticatedUser(false);
        setRequiredMethods(Render.METHOD_GET);
    }

    @Override
    public ActionResult doExecute(HttpServletRequest httpServletRequest, RenderContext renderContext, Resource resource,
                                  JCRSessionWrapper jcrSessionWrapper, Map<String, List<String>> parameters,
                                  URLResolver urlResolver) throws Exception {
        if (parameters.containsKey("code")) {
            final String token = getRequiredParameter(parameters, "code");
            if (StringUtils.isBlank(token)) {
                return ActionResult.BAD_REQUEST;
            }

            try {
                String siteKey = renderContext.getSite().getSiteKey();
                ConnectorConfig connectorConfig = settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY);
                jahiaOAuthService.extractAccessTokenAndExecuteMappers(connectorConfig, token, httpServletRequest.getRequestedSessionId());
                String returnUrl = connectorConfig.getProperty("returnUrl");
                if (StringUtils.isBlank(returnUrl)) {
                    returnUrl = (String) httpServletRequest.getSession().getAttribute(SESSION_REQUEST_URI);
                }
                if (StringUtils.isBlank(returnUrl) || StringUtils.endsWith(returnUrl, "/start") || StringUtils.endsWith(returnUrl, "/jahia/dashboard")) {
                    returnUrl = jcrTemplate.doExecuteWithSystemSessionAsUser(null, renderContext.getWorkspace(), renderContext.getMainResourceLocale(), systemSession ->
                            jahiaSitesService.getSiteByKey(siteKey, systemSession).getHome().getUrl());
                }
                // site query param is mandatory for the SSOValve in jahia-authentication module
                return new ActionResult(HttpServletResponse.SC_OK, returnUrl + "?site=" + siteKey, true, null);
            } catch (Exception e) {
                logger.error("", e);
            }
        } else {
            logger.error("Could not authenticate user with SSO, the callback from the server was missing mandatory parameters");
        }
        return ActionResult.BAD_REQUEST;
    }
}
