package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.sites.JahiaSitesService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.JahiaHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

@Path("/oauth/keycloak")
public class KeycloakCallbackResource {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakCallbackResource.class);

    public static final String SESSION_REQUEST_URI = "my.request_uri";

    @GET
    public Response authenticate(@Context HttpServletRequest httpServletRequest) {
        String token = httpServletRequest.getParameter("code");
        if (StringUtils.isNotEmpty(token)) {
            try {
                JCRTemplate jcrTemplate = BundleUtils.getOsgiService(JCRTemplate.class, null);
                JahiaSitesService jahiaSitesService = BundleUtils.getOsgiService(JahiaSitesService.class, null);
                String siteKey = JahiaHelper.getSiteKey(httpServletRequest, jcrTemplate, jahiaSitesService);
                if (StringUtils.isBlank(siteKey)) {
                    logger.warn("Site not found");
                    return Response.serverError().build();
                }
                BundleUtils.getOsgiService(JahiaOAuthService.class, null).extractAccessTokenAndExecuteMappers(
                        BundleUtils.getOsgiService(SettingsService.class, null).getConnectorConfig(
                                siteKey, KeycloakConnector.KEY), token, httpServletRequest.getRequestedSessionId());
                String returnUrl = (String) httpServletRequest.getSession().getAttribute(SESSION_REQUEST_URI);
                if (returnUrl == null || StringUtils.endsWith(returnUrl, "/start")) {
                    returnUrl = jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, systemSession ->
                            jahiaSitesService.getSiteByKey(siteKey, systemSession).getHome().getUrl());
                }
                // site query param is mandatory for the SSOValve in jahia-authentication module
                return Response.seeOther(UriBuilder.fromUri(returnUrl).queryParam("site", siteKey).build()).build();
            } catch (Exception e) {
                logger.error("", e);
            }
        } else {
            logger.error("Could not authenticate user with SSO, the callback from the server was missing mandatory parameters");
        }
        return Response.serverError().build();
    }
}
