package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.settings.SettingsBean;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.modules.external.users.UserGroupProviderConfiguration;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRContentUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Dictionary;
import java.util.Map;
import java.util.Properties;

@Component(service = UserGroupProviderConfiguration.class)
public class KeycloakUserGroupProviderConfiguration implements UserGroupProviderConfiguration {
    private static final long serialVersionUID = 7815956839511561636L;

    public static final String KEY = "keycloak";
    public static final String PROVIDER_KEY_PROP = KEY + ".provider.key";
    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserGroupProviderConfiguration.class);

    private KeycloakKarafConfigurationFactory keycloakKarafConfigurationFactory;
    private ConfigurationAdmin configurationAdmin;
    private SettingsBean settingsBean;
    private String moduleKey;

    @Reference
    private void setKeycloakConfigurationFactory(KeycloakKarafConfigurationFactory keycloakKarafConfigurationFactory) {
        this.keycloakKarafConfigurationFactory = keycloakKarafConfigurationFactory;
    }

    @Reference
    private void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Reference
    private void setSettingsBean(SettingsBean settingsBean) {
        this.settingsBean = settingsBean;
    }

    @Activate
    private void onActivate(BundleContext bundleContext) {
        moduleKey = BundleUtils.getModule(bundleContext.getBundle()).getId();
    }

    public KeycloakUserGroupProviderConfiguration() {
        moduleKey = "jahia-oauth-keycloak";
    }

    @Override
    public String getProviderClass() {
        return KeycloakUserGroupProvider.class.getCanonicalName();
    }

    @Override
    public String getName() {
        return KEY;
    }

    @Override
    public boolean isCreateSupported() {
        return true;
    }

    @Override
    public String getCreateJSP() {
        return "/modules/" + moduleKey + "/userGroupProviderEdit.jsp";
    }

    private static Properties getProperties(Map<String, Object> parameters) {
        Properties properties = new Properties();
        if (parameters.containsKey("configName")) properties.put("configName", parameters.get("configName"));
        if (parameters.containsKey("propValue." + KeycloakConfiguration.PROPERTY_TARGET_SITE) && StringUtils.isNotBlank((String) parameters.get("propValue." + KeycloakConfiguration.PROPERTY_TARGET_SITE)))
            properties.put(KeycloakConfiguration.PROPERTY_TARGET_SITE, parameters.get("propValue." + KeycloakConfiguration.PROPERTY_TARGET_SITE));
        if (parameters.containsKey("propValue." + KeycloakConfiguration.PROPERTY_BASE_URL) && StringUtils.isNotBlank((String) parameters.get("propValue." + KeycloakConfiguration.PROPERTY_BASE_URL)))
            properties.put(KeycloakConfiguration.PROPERTY_BASE_URL, parameters.get("propValue." + KeycloakConfiguration.PROPERTY_BASE_URL));
        if (parameters.containsKey("propValue." + KeycloakConfiguration.PROPERTY_REALM) && StringUtils.isNotBlank((String) parameters.get("propValue." + KeycloakConfiguration.PROPERTY_REALM)))
            properties.put(KeycloakConfiguration.PROPERTY_REALM, parameters.get("propValue." + KeycloakConfiguration.PROPERTY_REALM));
        if (parameters.containsKey("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_ID) && StringUtils.isNotBlank((String) parameters.get("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_ID)))
            properties.put(KeycloakConfiguration.PROPERTY_CLIENT_ID, parameters.get("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_ID));
        if (parameters.containsKey("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_SECRET) && StringUtils.isNotBlank((String) parameters.get("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_SECRET)))
            properties.put(KeycloakConfiguration.PROPERTY_CLIENT_SECRET, parameters.get("propValue." + KeycloakConfiguration.PROPERTY_CLIENT_SECRET));
        return properties;
    }

    @Override
    public String create(Map<String, Object> parameters, Map<String, Object> flashScope) throws Exception {
        Properties properties = getProperties(parameters);
        flashScope.put(KEY + "Properties", properties);

        // config name
        String configName = (String) parameters.get("configName");
        if (StringUtils.isBlank(configName)) {
            // if we didn't provide a not-blank config name, generate one
            configName = KEY + System.currentTimeMillis();
        }
        // normalize the name
        configName = JCRContentUtils.generateNodeName(configName);
        flashScope.put("configName", configName);

        // provider key
        String providerKey = KEY + "." + configName;
        configName = keycloakKarafConfigurationFactory.getName() + "-" + configName + ".cfg";

        // check that we don't already have a provider with that key
        String pid = keycloakKarafConfigurationFactory.getConfigPID(providerKey);
        if (pid != null) {
            throw new Exception("An " + KEY + " provider with key '" + providerKey + "' already exists");
        }


        File folder = new File(settingsBean.getJahiaVarDiskPath(), "karaf/etc");
        if (folder.exists()) {
            FileOutputStream out = new FileOutputStream(new File(folder, configName));
            try {
                properties.store(out, "");
            } finally {
                IOUtils.closeQuietly(out);
            }
        } else {
            Configuration configuration = configurationAdmin.createFactoryConfiguration(keycloakKarafConfigurationFactory.getName());
            properties.put(PROVIDER_KEY_PROP, providerKey);
            configuration.update((Dictionary) properties);
        }
        return providerKey;
    }

    @Override
    public boolean isEditSupported() {
        return true;
    }

    @Override
    public String getEditJSP() {
        return "/modules/" + moduleKey + "/userGroupProviderEdit.jsp";
    }

    @Override
    public void edit(String providerKey, Map<String, Object> parameters, Map<String, Object> flashScope) throws Exception {
        Properties properties = getProperties(parameters);
        flashScope.put(KEY + "Properties", properties);

        String configName;
        if (KEY.equals(providerKey)) {
            configName = keycloakKarafConfigurationFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith(KEY + ".")) {
            configName = keycloakKarafConfigurationFactory.getName() + "-" + providerKey.substring((KEY + ".").length()) + ".cfg";
        } else {
            throw new JahiaRuntimeException("Wrong provider key: " + providerKey);
        }

        File file = getExistingConfigFile(configName);
        if (file.exists()) {
            FileOutputStream out = new FileOutputStream(file);
            try {
                properties.store(out, "");
            } finally {
                IOUtils.closeQuietly(out);
            }
        } else {
            String pid = keycloakKarafConfigurationFactory.getConfigPID(providerKey);
            if (pid == null) {
                throw new Exception("Cannot find " + KEY + " provider " + providerKey);
            }
            Configuration configuration = configurationAdmin.getConfiguration(pid);
            properties.put(PROVIDER_KEY_PROP, providerKey);
            configuration.update((Dictionary) properties);
        }
    }

    @Override
    public boolean isDeleteSupported() {
        return true;
    }

    @Override
    public void delete(String providerKey, Map<String, Object> flashScope) throws Exception {
        String configName;
        if (KEY.equals(providerKey)) {
            configName = keycloakKarafConfigurationFactory.getName() + "-config.cfg";
        } else if (providerKey.startsWith(KEY + ".")) {
            configName = keycloakKarafConfigurationFactory.getName() + "-" + providerKey.substring((KEY + ".").length()) + ".cfg";
        } else {
            throw new JahiaRuntimeException("Wrong provider key: " + providerKey);
        }
        File file = getExistingConfigFile(configName);
        if (file.exists()) {
            if (!FileUtils.deleteQuietly(file)) {
                logger.error("Unable to delete the configuration file: {}", file.getPath());
            }
        } else {
            String pid = keycloakKarafConfigurationFactory.getConfigPID(providerKey);
            if (pid == null) {
                throw new JahiaRuntimeException("Cannot find provider " + providerKey);
            }
            configurationAdmin.getConfiguration(pid).delete();
        }
    }

    private File getExistingConfigFile(String configName) {
        File file = new File(settingsBean.getJahiaVarDiskPath(), "karaf/etc/" + configName);
        if (!file.exists()) {
            file = new File(settingsBean.getJahiaVarDiskPath(), "modules/" + configName);
        }
        return file;
    }
}
