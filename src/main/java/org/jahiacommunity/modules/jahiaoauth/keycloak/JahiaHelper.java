package org.jahiacommunity.modules.jahiaoauth.keycloak;

import org.jahia.api.Constants;
import org.jahia.api.content.JCRTemplate;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.http.HttpServletRequest;

public final class JahiaHelper {
    private static final Logger logger = LoggerFactory.getLogger(JahiaHelper.class);

    private JahiaHelper() {
        // No constructor
    }

    public static String getSiteKey(HttpServletRequest httpServletRequest, JCRTemplate jcrTemplate, JahiaSitesService jahiaSitesService) {
        try {
            return jcrTemplate.doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, systemSession -> {
                JahiaSite jahiaSite = jahiaSitesService.getSiteByServerName(httpServletRequest.getServerName(), systemSession);
                if (jahiaSite != null) {
                    return jahiaSite.getSiteKey();
                }

                jahiaSite = jahiaSitesService.getDefaultSite(systemSession);
                if (jahiaSite == null) {
                    return null;
                }
                return jahiaSite.getSiteKey();
            });
        } catch (RepositoryException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("", e);
            }
        }
        return null;
    }
}
