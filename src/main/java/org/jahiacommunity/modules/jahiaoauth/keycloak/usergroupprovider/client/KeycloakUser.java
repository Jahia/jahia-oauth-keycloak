package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.jackrabbit.util.Text;
import org.jahia.services.usermanager.JahiaUserImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class KeycloakUser implements Serializable {
    private static final long serialVersionUID = 4919896309626638014L;

    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private JahiaUserImpl jahiaUser;
    private List<String> groups;

    @JsonProperty("profile")
    private void setProfile(Map<String, String> profile) {
        firstName = profile.get("firstName");
        email = profile.get("email");
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEncodedUsername() {
        return Text.escapeIllegalJcrChars(username);
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public JahiaUserImpl getJahiaUser() {
        return jahiaUser;
    }

    public void setJahiaUser(JahiaUserImpl jahiaUser) {
        this.jahiaUser = jahiaUser;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
}
