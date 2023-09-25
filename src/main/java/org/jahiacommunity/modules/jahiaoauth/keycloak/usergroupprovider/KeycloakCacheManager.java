package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.config.CacheConfiguration;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.CacheHelper;
import org.jahia.services.cache.ModuleClassLoaderAwareCacheEntry;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.usermanager.JahiaGroupImpl;
import org.jahia.services.usermanager.JahiaUserImpl;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakGroup;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakUser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

@Component(service = KeycloakCacheManager.class)
public class KeycloakCacheManager {
    private static final String MODULE_NAME = "jahia-oauth-keycloak";
    private static final String USER_CACHE = "KeycloakUsersCache";
    private static final String GROUP_CACHE = "KeycloakGroupsCache";
    private static final int TIME_TO_IDLE = 3600;

    private static final Logger logger = LoggerFactory.getLogger(KeycloakCacheManager.class);

    private Ehcache groupCache;
    private Ehcache userCache;

    @Activate
    private void onActivate() {
        EhCacheProvider cacheProvider = (EhCacheProvider) SpringContextSingleton.getInstance().getContext().getBean("ehCacheProvider");
        final CacheManager cacheManager = cacheProvider.getCacheManager();
        userCache = cacheManager.getCache(USER_CACHE);
        if (userCache == null) {
            userCache = createCache(cacheManager, USER_CACHE);
        } else {
            userCache.removeAll();
        }
        groupCache = cacheManager.getCache(GROUP_CACHE);
        if (groupCache == null) {
            groupCache = createCache(cacheManager, GROUP_CACHE);
        } else {
            groupCache.removeAll();
        }
    }

    private static Ehcache createCache(CacheManager cacheManager, String cacheName) {
        CacheConfiguration cacheConfiguration = cacheManager.getConfiguration().getDefaultCacheConfiguration() != null ?
                cacheManager.getConfiguration().getDefaultCacheConfiguration().clone() :
                new CacheConfiguration();
        cacheConfiguration.setName(cacheName);
        cacheConfiguration.setEternal(false);
        cacheConfiguration.setTimeToIdleSeconds(TIME_TO_IDLE);
        // Create a new cache with the configuration
        Ehcache cache = new Cache(cacheConfiguration);
        cache.setName(cacheName);
        // Cache name has been set now we can initialize it by putting it in the manager.
        // Only Cache manager is initializing caches.
        return cacheManager.addCacheIfAbsent(cache);
    }

    @Deactivate
    private void onDeactivate() {
        // flush
        if (userCache != null) {
            userCache.removeAll();
        }
        if (groupCache != null) {
            groupCache.removeAll();
        }
    }

    public Optional<KeycloakUser> getUser(String providerKey, String siteKey, String username) {
        return Optional.ofNullable((KeycloakUser) CacheHelper.getObjectValue(userCache, getCacheNameKey(providerKey, siteKey, username)));
    }

    public Optional<KeycloakUser> getOrRefreshUser(String providerKey, String siteKey, String username, Supplier<Optional<KeycloakUser>> supplier) {
        return getUser(providerKey, siteKey, username).map(Optional::of).orElseGet(() -> {
            Optional<KeycloakUser> keycloakUser = supplier.get();
            keycloakUser.ifPresent(user -> cacheUser(providerKey, siteKey, user));
            return keycloakUser;
        });
    }

    public void cacheUser(String providerKey, String siteKey, KeycloakUser keycloakUser) {
        if (logger.isDebugEnabled()) {
            logger.debug("Caching user ({}): {} in site {}", keycloakUser.getUsername(), keycloakUser.getEmail(), siteKey);
        }
        Properties properties = new Properties();
        if (StringUtils.isNotBlank(keycloakUser.getFirstName())) {
            properties.put("j:firstName", keycloakUser.getFirstName());
        }
        if (StringUtils.isNotBlank(keycloakUser.getLastName())) {
            properties.put("j:lastName", keycloakUser.getLastName());
        }
        if (StringUtils.isNotBlank(keycloakUser.getEmail())) {
            properties.put("j:email", keycloakUser.getEmail());
        }
        keycloakUser.setJahiaUser(new JahiaUserImpl(keycloakUser.getId(), keycloakUser.getId(), properties, false, providerKey, siteKey));
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(keycloakUser, MODULE_NAME);
        userCache.put(new Element(getCacheNameKey(providerKey, siteKey, keycloakUser.getId()), cacheEntry));
    }

    public Optional<KeycloakGroup> getGroup(String providerKey, String siteKey, String groupname) {
        return Optional.ofNullable((KeycloakGroup) CacheHelper.getObjectValue(groupCache, getCacheNameKey(providerKey, siteKey, groupname)));
    }

    public Optional<KeycloakGroup> getOrRefreshGroup(String providerKey, String siteKey, String groupname, Supplier<Optional<KeycloakGroup>> supplier) {
        return getGroup(providerKey, siteKey, groupname).map(Optional::of).orElseGet(() -> {
            Optional<KeycloakGroup> keycloakGroup = supplier.get();
            keycloakGroup.ifPresent(group -> cacheGroup(providerKey, siteKey, group));
            return keycloakGroup;
        });
    }

    public void cacheGroup(String providerKey, String siteKey, KeycloakGroup keycloakGroup) {
        if (logger.isDebugEnabled()) {
            logger.debug("Caching group ({}): {} in site {}", keycloakGroup.getName(), keycloakGroup.getName(), siteKey);
        }
        Properties properties = new Properties();
        properties.put(Constants.JCR_TITLE, keycloakGroup.getName());
        keycloakGroup.setJahiaGroup(new JahiaGroupImpl(keycloakGroup.getId(), keycloakGroup.getId(), siteKey, properties));
        ModuleClassLoaderAwareCacheEntry cacheEntry = new ModuleClassLoaderAwareCacheEntry(keycloakGroup, MODULE_NAME);
        groupCache.put(new Element(getCacheNameKey(providerKey, siteKey, keycloakGroup.getId()), cacheEntry));
    }

    private static String getCacheNameKey(String providerKey, String siteKey, String objectName) {
        return providerKey + "_" + siteKey + "_" + KeycloakUserGroupProviderConfiguration.KEY + "_" + objectName;
    }

    public void flushCaches() {
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaUserManagerService.userPathByUserNameCache", true);
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaGroupManagerService.groupPathByGroupNameCache", true);
        CacheHelper.flushEhcacheByName("org.jahia.services.usermanager.JahiaGroupManagerService.membershipCache", true);
        CacheHelper.flushEhcacheByName(USER_CACHE, true);
        CacheHelper.flushEhcacheByName(GROUP_CACHE, true);
    }
}
