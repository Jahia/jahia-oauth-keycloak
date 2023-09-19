package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider;

import org.apache.commons.collections.CollectionUtils;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.modules.external.users.BaseUserGroupProvider;
import org.jahia.modules.external.users.GroupNotFoundException;
import org.jahia.modules.external.users.Member;
import org.jahia.modules.external.users.UserNotFoundException;
import org.jahia.services.usermanager.JahiaGroup;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakClientService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakGroup;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client.KeycloakUser;
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
    public JahiaUser getUser(String userId) throws UserNotFoundException {
        if (!isAvailable()) {
            throw new UserNotFoundException();
        }
        return keycloakCacheManager.getOrRefreshUser(getKey(), getSiteKey(), userId, () -> keycloakClientService.getUser(keycloakConfiguration, userId))
                .orElseThrow(() -> new UserNotFoundException("User '" + userId + "' not found.")).getJahiaUser();
    }

    @Override
    public JahiaGroup getGroup(String groupId) throws GroupNotFoundException {
        if (!isAvailable()) {
            throw new GroupNotFoundException();
        }
        if (JahiaGroupManagerService.PROTECTED_GROUPS.contains(groupId) || JahiaGroupManagerService.POWERFUL_GROUPS.contains(groupId)) {
            logger.warn("Group {} is protected", groupId);
            return null;
        }
        return keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupId, () -> keycloakClientService.getGroup(keycloakConfiguration, groupId))
                .orElseThrow(() -> new GroupNotFoundException("Group '" + groupId + "' not found.")).getJahiaGroup();
    }

    @Override
    public List<Member> getGroupMembers(String groupId) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }
        if (JahiaGroupManagerService.PROTECTED_GROUPS.contains(groupId) || JahiaGroupManagerService.POWERFUL_GROUPS.contains(groupId)) {
            logger.warn("Group {} is protected", groupId);
            return null;
        }
        // List of members in the groupId
        Optional<KeycloakGroup> group = keycloakCacheManager.getGroup(getKey(), getSiteKey(), groupId);
        if (!group.isPresent()) {
            return Collections.emptyList();
        }
        if (CollectionUtils.isNotEmpty(group.get().getMembers())) {
            return group.get().getMembers().stream().map(member -> new Member(member, Member.MemberType.USER))
                    .collect(Collectors.toList());
        }

        List<Member> members = new ArrayList<>();
        keycloakClientService.getGroupMembers(keycloakConfiguration, groupId).orElse(Collections.emptyList())
                .forEach(user -> members.add(new Member(user.getId(), Member.MemberType.USER)));
        keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupId, () -> keycloakClientService.getGroup(keycloakConfiguration, groupId))
                .ifPresent(g -> g.setMembers(members.stream().map(Member::getName).collect(Collectors.toList())));
        return Collections.unmodifiableList(members);
    }

    @Override
    public List<String> getMembership(Member member) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }
        if (member.getType() == Member.MemberType.GROUP) {
            return Collections.emptyList();
        }

        // List of groups this principal belongs to
        String userId = member.getName();
        Optional<KeycloakUser> user = keycloakCacheManager.getUser(getKey(), getSiteKey(), userId);
        if (!user.isPresent()) {
            return Collections.emptyList();
        }
        if (CollectionUtils.isNotEmpty(user.get().getGroups())) {
            return user.get().getGroups();
        }

        List<String> groups = new ArrayList<>();
        keycloakClientService.getMembership(keycloakConfiguration, userId).orElse(Collections.emptyList())
                .forEach(group -> groups.add(group.getId()));
        keycloakCacheManager.getOrRefreshUser(getKey(), getSiteKey(), userId, () -> keycloakClientService.getUser(keycloakConfiguration, userId))
                .ifPresent(u -> u.setGroups(groups));
        return Collections.unmodifiableList(groups);
    }

    @Override
    public List<String> searchUsers(Properties searchCriteria, long offset, long limit) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }

        // search one user in the cache
        if (searchCriteria.containsKey(PROP_USERNAME) && searchCriteria.size() == 1) {
            String userId = searchCriteria.getProperty(PROP_USERNAME);
            return keycloakCacheManager.getOrRefreshUser(getKey(), getSiteKey(), userId, () -> keycloakClientService.getUser(keycloakConfiguration, userId))
                    .map(keycloakUser -> Collections.singletonList(keycloakUser.getId()))
                    .orElse(Collections.emptyList());
        }

        Optional<List<KeycloakUser>> keycloakUsers;
        if (searchCriteria.containsKey("*")) {
            keycloakUsers = keycloakClientService.getUsers(keycloakConfiguration, searchCriteria.getProperty("*").replace("*", ""), offset, limit);
        } else {
            keycloakUsers = keycloakClientService.getUsers(keycloakConfiguration, "", offset, limit);
        }
        List<String> userIds = new ArrayList<>();
        keycloakUsers.orElse(Collections.emptyList())
                .forEach(user -> {
                    userIds.add(user.getId());
                    keycloakCacheManager.cacheUser(getKey(), getSiteKey(), user);
                });
        return Collections.unmodifiableList(userIds);
    }

    @Override
    public List<String> searchGroups(Properties searchCriteria, long offset, long limit) {
        if (!isAvailable()) {
            throw new JahiaRuntimeException("Service not available");
        }

        // search one group in the cache
        if (searchCriteria.containsKey(PROP_GROUPNAME) && searchCriteria.size() == 1) {
            String groupId = searchCriteria.getProperty(PROP_GROUPNAME);
            if (JahiaGroupManagerService.PROTECTED_GROUPS.contains(groupId) || JahiaGroupManagerService.POWERFUL_GROUPS.contains(groupId)) {
                logger.warn("Group {} is protected", groupId);
                return Collections.emptyList();
            }
            return keycloakCacheManager.getOrRefreshGroup(getKey(), getSiteKey(), groupId, () -> keycloakClientService.getGroup(keycloakConfiguration, groupId))
                    .map(keycloakGroup -> Collections.singletonList(keycloakGroup.getId()))
                    .orElse(Collections.emptyList());
        }

        Optional<List<KeycloakGroup>> keycloakGroups;
        if (searchCriteria.containsKey("*")) {
            keycloakGroups = keycloakClientService.getGroups(keycloakConfiguration, searchCriteria.getProperty("*").replace("*", ""), offset, limit);
        } else {
            keycloakGroups = keycloakClientService.getGroups(keycloakConfiguration, "", offset, limit);
        }
        List<String> groupIds = new ArrayList<>();
        keycloakGroups.orElse(Collections.emptyList())
                .forEach(group -> {
                    groupIds.add(group.getId());
                    keycloakCacheManager.cacheGroup(getKey(), getSiteKey(), group);
                });
        return Collections.unmodifiableList(groupIds);
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
