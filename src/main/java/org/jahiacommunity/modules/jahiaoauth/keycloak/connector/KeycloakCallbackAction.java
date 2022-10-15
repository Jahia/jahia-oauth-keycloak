package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.api.settings.SettingsBean;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.jahiaauth.service.JahiaAuthConstants;
import org.jahia.modules.jahiaauth.service.JahiaAuthMapperService;
import org.jahia.modules.jahiaauth.service.MappedProperty;
import org.jahia.modules.jahiaauth.service.SettingsService;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthService;
import org.jahia.osgi.BundleUtils;
import org.jahia.osgi.FrameworkService;
import org.jahia.params.valves.AuthValveContext;
import org.jahia.params.valves.BaseLoginEvent;
import org.jahia.params.valves.CookieAuthValveImpl;
import org.jahia.params.valves.LoginEngineAuthValveImpl;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component(service = Action.class)
public class KeycloakCallbackAction extends Action {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakCallbackAction.class);

    private static final String NAME = "keycloakOAuthCallbackAction";
    public static final String SESSION_REQUEST_URI = "my.request_uri";

    private JahiaOAuthService jahiaOAuthService;
    private SettingsService settingsService;
    private JahiaAuthMapperService jahiaAuthMapperService;
    private JahiaUserManagerService jahiaUserManagerService;
    private SettingsBean settingsBean;

    @Reference
    private void setJahiaOAuthService(JahiaOAuthService jahiaOAuthService) {
        this.jahiaOAuthService = jahiaOAuthService;
    }

    @Reference
    private void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Reference
    private void setJahiaAuthMapperService(JahiaAuthMapperService jahiaAuthMapperService) {
        this.jahiaAuthMapperService = jahiaAuthMapperService;
    }

    @Reference
    private void setJahiaUserManagerService(JahiaUserManagerService jahiaUserManagerService) {
        this.jahiaUserManagerService = jahiaUserManagerService;
    }

    @Reference
    private void setSettingsBean(SettingsBean settingsBean) {
        this.settingsBean = settingsBean;
    }

    @Activate
    public void onActivate() {
        setName(NAME);
        setRequireAuthenticatedUser(false);
        setRequiredMethods(HttpMethod.GET.name());
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
                jahiaOAuthService.extractAccessTokenAndExecuteMappers(settingsService.getConnectorConfig(siteKey, KeycloakConnector.KEY), token, httpServletRequest.getRequestedSessionId());
                if (!authenticateUser(renderContext)) {
                    return ActionResult.BAD_REQUEST;
                }
                String returnUrl = (String) httpServletRequest.getSession().getAttribute(SESSION_REQUEST_URI);
                if (returnUrl == null || StringUtils.endsWith(returnUrl, "/start")) {
                    returnUrl = renderContext.getSite().getHome().getUrl();
                }
                // WARN: site query param is mandatory for the SSOValve in jahia-authentication module
                return new ActionResult(HttpServletResponse.SC_OK, returnUrl + "?site=" + siteKey, true, null);
            } catch (Exception e) {
                logger.error("", e);
            }
        } else {
            logger.error("Could not authenticate user with SSO, the callback from the server was missing mandatory parameters");
        }
        return ActionResult.BAD_REQUEST;
    }

    private boolean authenticateUser(RenderContext renderContext) {
        boolean ok = false;
        HttpServletRequest httpServletRequest = renderContext.getRequest();
        AuthValveContext authContext = new AuthValveContext(httpServletRequest, renderContext.getResponse(), JCRSessionFactory.getInstance());

        String originalSessionId = httpServletRequest.getSession(false).getId();

        String userId = findUserId(jahiaAuthMapperService.getMapperResultsForSession(originalSessionId));
        if (StringUtils.isNotBlank(userId)) {
            JCRUserNode jcrUserNode = jahiaUserManagerService.lookupUser(userId, renderContext.getSite().getSiteKey());
            if (jcrUserNode != null) {
                if (!jcrUserNode.isAccountLocked()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("User {} logged in.", jcrUserNode);
                    }

                    JahiaUser jahiaUser = jcrUserNode.getJahiaUser();

                    if (httpServletRequest.getSession(false) != null) {
                        httpServletRequest.getSession().invalidate();
                    }

                    if (!originalSessionId.equals(httpServletRequest.getSession().getId())) {
                        jahiaAuthMapperService.updateCacheEntry(originalSessionId, httpServletRequest.getSession().getId());
                    }

                    httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.OK);
                    authContext.getSessionFactory().setCurrentUser(jahiaUser);

                    // do a switch to the user's preferred language
                    if (settingsBean.isConsiderPreferredLanguageAfterLogin()) {
                        Locale preferredUserLocale = UserPreferencesHelper.getPreferredLocale(jcrUserNode, LanguageCodeConverters.resolveLocaleForGuest(httpServletRequest));
                        httpServletRequest.getSession().setAttribute(Constants.SESSION_LOCALE, preferredUserLocale);
                    }

                    String useCookie = httpServletRequest.getParameter("useCookie");
                    if ("on".equals(useCookie)) {
                        // the user has indicated he wants to use cookie authentication
                        CookieAuthValveImpl.createAndSendCookie(authContext, jcrUserNode, settingsBean.getCookieAuthConfig());
                    }

                    Map<String, Object> m = new HashMap<>();
                    m.put("user", jahiaUser);
                    m.put("authContext", authContext);
                    m.put("source", this);
                    FrameworkService.sendEvent("org/jahia/usersgroups/login/LOGIN", m, false);

                    ok = true;
                } else {
                    logger.warn("Login failed: account for user {} is locked.", jcrUserNode.getName());
                    httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.ACCOUNT_LOCKED);
                }
            } else {
                logger.warn("Login failed. Unknown username {}", userId);
                httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.UNKNOWN_USER);
            }
        } else {
            logger.warn("Login failed. Unknown username.");
            httpServletRequest.setAttribute(LoginEngineAuthValveImpl.VALVE_RESULT, LoginEngineAuthValveImpl.UNKNOWN_USER);
        }
        return ok;
    }

    private static String findUserId(Map<String, Map<String, MappedProperty>> allMapperResult) {
        for (Map<String, MappedProperty> mapperResult : allMapperResult.values()) {
            if (mapperResult.containsKey(JahiaAuthConstants.SSO_LOGIN)) {
                return (String) mapperResult.get(JahiaAuthConstants.SSO_LOGIN).getValue();
            }
        }
        return null;
    }
}
