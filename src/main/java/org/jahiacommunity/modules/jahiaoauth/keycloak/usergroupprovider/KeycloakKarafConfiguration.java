package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakClientService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

public class KeycloakKarafConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakKarafConfiguration.class);

    private final String providerKey;
    private KeycloakUserGroupProvider keycloakUserGroupProvider;

    public KeycloakKarafConfiguration(Dictionary<String, ?> dictionary) {
        providerKey = computeProviderKey(dictionary);
    }

    private static String computeProviderKey(Dictionary<String, ?> dictionary) {
        String provideKey = (String) dictionary.get(KeycloakUserGroupProviderConfiguration.PROVIDER_KEY_PROP);
        if (provideKey != null) {
            return provideKey;
        }
        String filename = (String) dictionary.get("felix.fileinstall.filename");
        String factoryPid = (String) dictionary.get(ConfigurationAdmin.SERVICE_FACTORYPID);
        String confId;
        if (StringUtils.isBlank(filename)) {
            confId = (String) dictionary.get(Constants.SERVICE_PID);
            if (StringUtils.startsWith(confId, factoryPid + ".")) {
                confId = StringUtils.substringAfter(confId, factoryPid + ".");
            }
        } else {
            confId = StringUtils.removeEnd(StringUtils.substringAfter(filename, factoryPid + "-"), ".cfg");
        }
        return (StringUtils.isBlank(confId) || "config".equals(confId)) ? KeycloakUserGroupProviderConfiguration.KEY : (KeycloakUserGroupProviderConfiguration.KEY + "." + confId);
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void createUserGroupProvider(ExternalUserGroupService externalUserGroupService, KeycloakCacheManager keycloakCacheManager, KeycloakClientService keycloakClientService, BundleContext bundleContext, Dictionary<String, ?> dictionary) {
        if (keycloakUserGroupProvider == null) {
            keycloakUserGroupProvider = new KeycloakUserGroupProvider(keycloakCacheManager, keycloakClientService);
            keycloakUserGroupProvider.setExternalUserGroupService(externalUserGroupService);
            keycloakUserGroupProvider.setBundleContext(bundleContext);
        } else {
            keycloakUserGroupProvider.unregister();
        }

        KeycloakConfiguration keycloakConfiguration = new KeycloakConfiguration(
                (String) dictionary.get(KeycloakConfiguration.PROPERTY_TARGET_SITE),
                (String) dictionary.get(KeycloakConfiguration.PROPERTY_BASE_URL),
                (String) dictionary.get(KeycloakConfiguration.PROPERTY_REALM),
                (String) dictionary.get(KeycloakConfiguration.PROPERTY_CLIENT_ID),
                (String) dictionary.get(KeycloakConfiguration.PROPERTY_CLIENT_SECRET));
        keycloakUserGroupProvider.setKey(providerKey);
        keycloakUserGroupProvider.setKeycloakConfiguration(keycloakConfiguration);
        // Activate (again)
        keycloakUserGroupProvider.register();
    }

    public void unregister() {
        if (keycloakUserGroupProvider != null) {
            keycloakUserGroupProvider.unregister();
            keycloakUserGroupProvider = null;
        }
    }
}
