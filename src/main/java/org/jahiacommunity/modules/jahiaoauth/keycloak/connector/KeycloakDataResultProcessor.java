package org.jahiacommunity.modules.jahiaoauth.keycloak.connector;

import org.jahia.modules.jahiaauth.service.ConnectorConfig;
import org.jahia.modules.jahiaauth.service.ConnectorResultProcessor;
import org.jahia.modules.jahiaauth.service.JahiaAuthConstants;
import org.jahia.modules.jahiaauth.service.JahiaAuthMapperService;
import org.jahia.modules.jahiaauth.service.MappedProperty;
import org.jahia.modules.jahiaauth.service.MappedPropertyInfo;
import org.jahia.modules.jahiaoauth.service.JahiaOAuthConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
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
        // store tokenData to cache
        Map<String, MappedProperty> data = new HashMap<>();
        data.put(JahiaAuthConstants.SSO_LOGIN, new MappedProperty(new MappedPropertyInfo(JahiaAuthConstants.SSO_LOGIN), results.get(KeycloakConnector.SSO_LOGIN)));
        data.put(JahiaOAuthConstants.TOKEN_DATA, new MappedProperty(new MappedPropertyInfo(JahiaOAuthConstants.TOKEN_DATA), results.get(JahiaOAuthConstants.TOKEN_DATA)));
        jahiaAuthMapperService.cacheMapperResults(KeycloakConnector.KEY, RequestContextHolder.getRequestAttributes().getSessionId(), data);
    }
}
