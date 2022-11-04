package org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.jackrabbit.util.Text;
import org.jahia.services.usermanager.JahiaGroupImpl;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class KeycloakGroup implements Serializable {
    private static final long serialVersionUID = 9096799222792186690L;

    private String id;
    private String name;
    private String path;
    private List<KeycloakGroup> subGroups;
    private JahiaGroupImpl jahiaGroup;
    private List<String> members;

    @JsonProperty("profile")
    private void setProfile(Map<String, String> profile) {
        name = profile.get("name");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEncodedName() {
        return Text.escapeIllegalJcrChars(name);
    }

    public String getPath() {
        return path;
    }

    public List<KeycloakGroup> getSubGroups() {
        return subGroups;
    }

    public JahiaGroupImpl getJahiaGroup() {
        return jahiaGroup;
    }

    public void setJahiaGroup(JahiaGroupImpl jahiaGroup) {
        this.jahiaGroup = jahiaGroup;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }
}
