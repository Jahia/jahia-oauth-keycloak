package org.jahiacommunity.modules.jahiaoauth.keycloak;

import org.glassfish.jersey.server.ResourceConfig;
import org.jahiacommunity.modules.jahiaoauth.keycloak.connector.KeycloakCallbackResource;

public class JaxRsConfig extends ResourceConfig {
    public JaxRsConfig() {
        super(KeycloakCallbackResource.class);
    }
}
