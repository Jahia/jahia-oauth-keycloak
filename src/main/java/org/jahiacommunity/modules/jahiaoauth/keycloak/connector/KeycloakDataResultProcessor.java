package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.ConnectorResultProcessor;
import org.jahia.modules.jahiaauth.service.JahiaAuthConstants;
import org.jahia.modules.jahiaauth.service.JahiaAuthMapperService;
import org.jahia.modules.jahiaauth.service.MappedProperty;
import org.jahia.modules.jahiaauth.service.MappedPropertyInfo;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.Map;

@Component(service = ConnectorResultProcessor.class, immediate = true)
public class KeycloakDataResultProcessor implements ConnectorResultProcessor {
    private JahiaAuthMapperService jahiaAuthMapperService;

    @Reference
    private void setJahiaAuthMapperService(JahiaAuthMapperService jahiaAuthMapperService) {
        this.jahiaAuthMapperService = jahiaAuthMapperService;
    }

    @Override
    public void execute(ConnectorConfig connectorConfig, Map<String, Object> results) {
        if (results.containsKey(KeycloakConnector.SSO_LOGIN)) {
            // store login to cache
            jahiaAuthMapperService.cacheMapperResults(KeycloakConnector.KEY, RequestContextHolder.getRequestAttributes().getSessionId(),
                    Collections.singletonMap(JahiaAuthConstants.SSO_LOGIN, new MappedProperty(
                            new MappedPropertyInfo(JahiaAuthConstants.SSO_LOGIN), results.get(KeycloakConnector.SSO_LOGIN))));
        }
    }
}
