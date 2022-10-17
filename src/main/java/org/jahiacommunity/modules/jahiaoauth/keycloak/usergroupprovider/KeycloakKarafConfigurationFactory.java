package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import org.jahia.modules.external.users.ExternalUserGroupService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakClientService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(service = {KeycloakKarafConfigurationFactory.class, ManagedServiceFactory.class}, property = Constants.SERVICE_PID + "=org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider", immediate = true)
public class KeycloakKarafConfigurationFactory implements ManagedServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakKarafConfigurationFactory.class);

    private ConfigurationAdmin configurationAdmin;
    private KeycloakCacheManager keycloakCacheManager;
    private KeycloakClientService keycloakClientService;
    private ExternalUserGroupService externalUserGroupService;
    private BundleContext bundleContext;

    private final Map<String, KeycloakKarafConfiguration> keycloakKarafConfigurations;
    private final Map<String, String> pidsByProviderKey;

    public KeycloakKarafConfigurationFactory() {
        keycloakKarafConfigurations = new HashMap<>();
        pidsByProviderKey = new HashMap<>();
    }

    @Reference
    private void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference
    private void setKeycloakCacheManager(KeycloakCacheManager keycloakCacheManager) {
        this.keycloakCacheManager = keycloakCacheManager;
    }

    @Reference
    private void setKeycloakClientService(KeycloakClientService keycloakClientService) {
        this.keycloakClientService = keycloakClientService;
    }

    @Reference
    private void setExternalUserGroupService(ExternalUserGroupService externalUserGroupService) {
        this.externalUserGroupService = externalUserGroupService;
    }

    @Activate
    private void onActivate(BundleContext context) {
        this.bundleContext = context;
    }

    @Deactivate
    private void onDeactivate() {
        for (KeycloakKarafConfiguration config : keycloakKarafConfigurations.values()) {
            config.unregister();
        }
        keycloakKarafConfigurations.clear();
    }

    @Override
    public void updated(String pid, Dictionary<String, ?> dictionary) throws ConfigurationException {
        KeycloakKarafConfiguration keycloakKarafConfiguration;
        if (this.keycloakKarafConfigurations.containsKey(pid)) {
            keycloakKarafConfiguration = this.keycloakKarafConfigurations.get(pid);
        } else {
            keycloakKarafConfiguration = new KeycloakKarafConfiguration(dictionary);
            this.keycloakKarafConfigurations.put(pid, keycloakKarafConfiguration);
            deleteConfig(pidsByProviderKey.put(keycloakKarafConfiguration.getProviderKey(), pid));
        }
        keycloakKarafConfiguration.createUserGroupProvider(externalUserGroupService, keycloakCacheManager, keycloakClientService, bundleContext, dictionary);
        keycloakCacheManager.flushCaches();
    }

    private void deleteConfig(String pid) {
        if (pid == null) {
            return;
        }
        try {
            Configuration cfg = configurationAdmin.getConfiguration(pid);
            if (cfg != null) {
                cfg.delete();
            }
        } catch (IOException e) {
            logger.error("Unable to delete Keycloak configuration for pid " + pid, e);
        }
    }

    @Override
    public void deleted(String pid) {
        KeycloakKarafConfiguration keycloakKarafConfiguration = keycloakKarafConfigurations.remove(pid);
        String existingPid = keycloakKarafConfiguration != null ? pidsByProviderKey.get(keycloakKarafConfiguration.getProviderKey()) : null;
        if (existingPid != null && existingPid.equals(pid)) {
            pidsByProviderKey.remove(keycloakKarafConfiguration.getProviderKey());
            keycloakKarafConfiguration.unregister();
            keycloakCacheManager.flushCaches();
        }
    }

    public String getName() {
        return KeycloakKarafConfigurationFactory.class.getPackage().getName();
    }

    public String getConfigPID(String providerKey) {
        return pidsByProviderKey.get(providerKey);
    }
}
