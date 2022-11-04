package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.net.URIBuilder;
import org.jahia.services.notification.HttpClientService;
import org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.KeycloakConfiguration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(service = KeycloakClientService.class)
public class KeycloakClientService {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakClientService.class);

    private HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock;

    public KeycloakClientService() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        lock = new ReentrantLock();
    }

    @Reference
    private void setHttpClientService(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    public Optional<KeycloakUser> getUser(KeycloakConfiguration keycloakConfiguration, String username) {
        return callEndpoint(keycloakConfiguration, "/users", Collections.singletonMap("username", username), -1, -1, KeycloakUser.class)
                .filter(CollectionUtils::isNotEmpty)
                .map(users -> users.get(0));

    }

    public Optional<List<KeycloakUser>> getUsers(KeycloakConfiguration keycloakConfiguration, String search, long offset, long limit) {
        return callEndpoint(keycloakConfiguration, "/users", Collections.singletonMap("username", search), offset, limit, KeycloakUser.class);
    }

    public Optional<KeycloakGroup> getGroup(KeycloakConfiguration keycloakConfiguration, String groupname) {
        return callEndpoint(keycloakConfiguration, "/groups", Collections.singletonMap("search", groupname), -1, -1, KeycloakGroup.class)
                .filter(CollectionUtils::isNotEmpty)
                .map(users -> users.get(0));
    }

    public Optional<List<KeycloakGroup>> getGroups(KeycloakConfiguration keycloakConfiguration, String search, long offset, long limit) {
        return callEndpoint(keycloakConfiguration, "/groups", Collections.singletonMap("search", search), offset, limit, KeycloakGroup.class)
                .map(keycloakGroups -> keycloakGroups.stream().flatMap(this::flatMapRecursive).collect(Collectors.toList()));
    }

    private Stream<KeycloakGroup> flatMapRecursive(KeycloakGroup keycloakGroup) {
        return Stream.concat(Stream.of(keycloakGroup), Optional.ofNullable(keycloakGroup.getSubGroups())
                .orElseGet(Collections::emptyList)
                .stream().flatMap(this::flatMapRecursive));
    }

    public Optional<List<KeycloakUser>> getGroupMembers(KeycloakConfiguration keycloakConfiguration, String groupId) {
        return callEndpoint(keycloakConfiguration, "/groups/" + groupId + "/members", null, -1, -1, KeycloakUser.class);
    }

    public Optional<List<KeycloakGroup>> getMembership(KeycloakConfiguration keycloakConfiguration, String userId) {
        return callEndpoint(keycloakConfiguration, "/users/" + userId + "/groups", null, -1, -1, KeycloakGroup.class);
    }

    private <T> Optional<List<T>> callEndpoint(KeycloakConfiguration keycloakConfiguration, String api, Map<String, String> params, long offset, long limit, Class<T> clazz) {
        lock.lock();
        try {
            refreshToken(keycloakConfiguration);
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            headers.put(HttpHeaders.CACHE_CONTROL, "no-cache");
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + keycloakConfiguration.getAccessToken());
            StringBuilder url = new StringBuilder();
            url.append(keycloakConfiguration.getBaseUrl()).append("/admin/realms/").append(keycloakConfiguration.getRealm());
            url.append(api);
            try {
                URIBuilder uriBuilder = new URIBuilder(url.toString());
                if (MapUtils.isNotEmpty(params)) {
                    params.forEach(uriBuilder::addParameter);
                }
                if (offset > 0) {
                    uriBuilder.addParameter("first", String.valueOf(offset));
                }
                if (limit > 0) {
                    uriBuilder.addParameter("max", String.valueOf(limit));
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Call: {}", uriBuilder);
                }
                String result = httpClientService.executeGet(uriBuilder.toString(), headers);
                return Optional.ofNullable(result).map(data -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug(data);
                    }
                    try {
                        return objectMapper.readValue(data, objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
                    } catch (JsonProcessingException e) {
                        logger.error("Invalid json data: {}", data, e);
                        return null;
                    }
                });
            } catch (IllegalArgumentException | URISyntaxException e) {
                logger.error("", e);
                return Optional.empty();
            }
        } finally {
            lock.unlock();
        }
    }

    private void refreshToken(KeycloakConfiguration keycloakConfiguration) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", keycloakConfiguration.getClientId());
        params.put("client_secret", keycloakConfiguration.getClientSecret());
        if (keycloakConfiguration.isRefreshPossible()) {
            params.put("grant_type", "refresh_token");
            params.put("refresh_token", keycloakConfiguration.getRefreshToken());
        } else {
            params.put("grant_type", "client_credentials");
            params.put("username", keycloakConfiguration.getClientId());
        }

        Map<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);

        String data = httpClientService.executePost(keycloakConfiguration.getBaseUrl() + "/realms/" + keycloakConfiguration.getRealm() + "/protocol/openid-connect/token", params, headers);
        if (StringUtils.isNotBlank(data)) {
            try {
                Map<String, Object> tokenData = objectMapper.readValue(data, objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                if (tokenData != null && tokenData.containsKey("access_token")) {
                    keycloakConfiguration.setAccessToken((String) tokenData.get("access_token"));
                }
                if (tokenData != null && tokenData.containsKey("refresh_token")) {
                    keycloakConfiguration.setRefreshToken((String) tokenData.get("refresh_token"));
                }
                if (tokenData != null && tokenData.containsKey("expires_in")) {
                    keycloakConfiguration.setRefreshExpirationDateTime(LocalDateTime.now().plusSeconds((int) tokenData.get("expires_in") - KeycloakConfiguration.REFRESH_EXPIRATION_DURATION));
                }
            } catch (JsonProcessingException e) {
                logger.error("Invalid json token: {}", data, e);
            }
        }
    }
}
