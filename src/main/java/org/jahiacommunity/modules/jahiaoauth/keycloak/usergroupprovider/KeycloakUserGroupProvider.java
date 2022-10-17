package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import org.apache.commons.collections.CollectionUtils;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakClientService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakGroup;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakUser;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.modules.external.users.BaseUserGroupProvider;
import org.jahia.modules.external.users.GroupNotFoundException;
import org.jahia.modules.external.users.Member;
import org.jahia.modules.external.users.UserNotFoundException;
import org.jahia.services.usermanager.JahiaGroup;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

public class KeycloakUserGroupProvider extends BaseUserGroupProvider {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserGroupProvider.class);

    private static final String PROP_USERNAME = "username";
    private static final String PROP_GROUPNAME = "groupname";

    private final KeycloakCacheManager keycloakCacheManager;
    private final KeycloakClientService keycloakClientService;
    private KeycloakConfiguration keycloakConfiguration;

    public KeycloakUserGroupProvider(KeycloakCacheManager keycloakCacheManager, KeycloakClientService keycloakClientService) {
        this.keycloakCacheManager = keycloakCacheManager;
        this.keycloakClientService = keycloakClientService;
    }

    public void setKeycloakConfiguration(KeycloakConfiguration keycloakConfiguration) {
        this.keycloakConfiguration = keycloakConfiguration;
    }

    @Override
    protected String getSiteKey() {
        if (keycloakConfiguration == null) {
            return null;
        }
        return keycloakConfiguration.getTargetSite();
    }

    @Override
    public JahiaUser getUser(String username) throws UserNotFoundException {
        if (!isAvailable()) {
            throw new UserNotFoundException();
        }
        return keycloakCacheManager.getOrRefreshUser(getKey(), username, () -> keycloakClientService.getUser(keycloakConfiguration, username))
                .orElseThrow(() -> new UserNotFoundException("User '" + username + "' not found.")).getJahiaUser();
    }

    @Override
    public JahiaGroup getGroup(String groupEncodedName) throws GroupNotFoundException {
        if (!isAvailable()) {
            throw new GroupNotFoundException();
        }
        return keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupEncodedName, () -> keycloakClientService.getGroup(keycloakConfiguration, groupEncodedName))
                .orElseThrow(() -> new GroupNotFoundException("Group '" + groupEncodedName + "' not found.")).getJahiaGroup();
    }

    @Override
    public List<Member> getGroupMembers(String groupEncodedName) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }
        // List of members in the groupname
        Optional<KeycloakGroup> group = keycloakCacheManager.getGroup(getKey(), groupEncodedName);
        if (group.isPresent() && CollectionUtils.isNotEmpty(group.get().getMembers())) {
            return group.get().getMembers().stream().map(member -> new Member(member, Member.MemberType.USER))
                    .collect(Collectors.toList());
        }

        List<Member> members = new ArrayList<>();
        if (group.isPresent()) {
            keycloakClientService.getGroupMembers(keycloakConfiguration, group.get().getId()).orElse(Collections.emptyList())
                    .forEach(user -> members.add(new Member(user.getEncodedUsername(), Member.MemberType.USER)));
            keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupEncodedName, () -> keycloakClientService.getGroup(keycloakConfiguration, group.get().getName()))
                    .ifPresent(g -> g.setMembers(members.stream().map(Member::getName).collect(Collectors.toList())));
        }
        return Collections.unmodifiableList(members);
    }

    @Override
    public List<String> getMembership(Member member) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }

        // List of groups this principal belongs to
        String username = member.getName();
        Optional<KeycloakUser> user = keycloakCacheManager.getUser(getKey(), username);
        if (user.isPresent() && CollectionUtils.isNotEmpty(user.get().getGroups())) {
            return user.get().getGroups();
        }

        List<String> groups = new ArrayList<>();
        if (user.isPresent()) {
            keycloakClientService.getMembership(keycloakConfiguration, user.get().getId()).orElse(Collections.emptyList())
                    .forEach(group -> groups.add(group.getEncodedName()));
            keycloakCacheManager.getOrRefreshUser(getKey(), username, () -> keycloakClientService.getUser(keycloakConfiguration, username))
                    .ifPresent(u -> u.setGroups(groups));
        }
        return Collections.unmodifiableList(groups);
    }

    @Override
    public List<String> searchUsers(Properties searchCriteria, long offset, long limit) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }

        // search one user in the cache
        if (searchCriteria.containsKey(PROP_USERNAME) && searchCriteria.size() == 1 && !searchCriteria.getProperty(PROP_USERNAME).contains("*")) {
            String username = (String) searchCriteria.get(PROP_USERNAME);
            return keycloakCacheManager.getOrRefreshUser(getKey(), username, () -> keycloakClientService.getUser(keycloakConfiguration, username))
                    .map(user -> Collections.singletonList(user.getEncodedUsername()))
                    .orElse(Collections.emptyList());
        }

        List<String> users = new ArrayList<>();
        keycloakClientService.getUsers(keycloakConfiguration, "", offset, limit).orElse(Collections.emptyList())
                .forEach(user -> {
                    users.add(user.getEncodedUsername());
                    keycloakCacheManager.cacheUser(getKey(), user);
                });
        return Collections.unmodifiableList(users);
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria, long offset, long limit) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }

        // search one group in the cache
        if (searchCriteria.containsKey(PROP_GROUPNAME) && searchCriteria.size() == 1 && !searchCriteria.getProperty(PROP_GROUPNAME).contains("*")) {
            String groupname = (String) searchCriteria.get(PROP_GROUPNAME);
            return keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupname, () -> keycloakClientService.getGroup(keycloakConfiguration, groupname))
                    .map(group -> Collections.singletonList(group.getEncodedName()))
                    .orElse(Collections.emptyList());
        }

        List<String> groups = new ArrayList<>();
        keycloakClientService.getGroups(keycloakConfiguration, "", offset, limit).orElse(Collections.emptyList())
                .forEach(group -> {
                    groups.add(group.getEncodedName());
                    keycloakCacheManager.cacheGroup(getKey(), getSiteKey(), group);
                });
        return Collections.unmodifiableList(groups);
    }

    @Override
    public boolean verifyPassword(String userName, String userPassword) {
        return false;
    }

    @Override
    public boolean supportsGroups() {
        return isAvailable();
    }

    @Override
    public boolean isAvailable() {
        return keycloakClientService != null && keycloakConfiguration != null;
    }
}
