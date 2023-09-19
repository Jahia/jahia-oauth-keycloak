<%@ page import="org.jahia.osgi.BundleUtils" %>
<%@ page import="org.jahiacommunity.modules.jahiaoauth.keycloak.usergroupprovider.KeycloakKarafConfigurationFactory" %>
<%@ page import="org.osgi.service.cm.ConfigurationAdmin" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Dictionary" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%
    KeycloakKarafConfigurationFactory keycloakKarafConfigurationFactory = BundleUtils.getOsgiService(KeycloakKarafConfigurationFactory.class, null);
    ConfigurationAdmin configurationAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
    String providerKey = (String) pageContext.findAttribute("providerKey");
    if (providerKey != null) {
        Dictionary<String, Object> properties = configurationAdmin.getConfiguration(keycloakKarafConfigurationFactory.getConfigPID(providerKey)).getProperties();
        Enumeration<String> keys = properties.keys();
        Map<String, Object> keycloakProperties = new HashMap<>();
        String key;
        while (keys.hasMoreElements()) {
            key = keys.nextElement();
            keycloakProperties.put(key, properties.get(key));
        }
        pageContext.setAttribute("keycloakProperties", keycloakProperties);
    } else {
        pageContext.setAttribute("keycloakProperties", Collections.emptyMap());
    }
%>
<utility:setBundle basename="resources.jahia-oauth-keycloak" var="bundle"/>
<jcr:jqom statement="SELECT * FROM [jnt:virtualsite] WHERE ISCHILDNODE('/sites') AND localname() <> 'systemsite'"
          var="sites"/>
<datalist id="sites">
    <c:forEach items="${sites.nodes}" var="site">
        <option value="${site.name}"></option>
    </c:forEach>
</datalist>
<template:addResources type="javascript" resources="jquery.min.js,jquery.form.min.js"/>
<div class="row">
    <div class="col-md-12">
        <fieldset title="local">
            <div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label"><fmt:message bundle="${bundle}"
                                                                  key="KeycloakUserGroupProvider.name"/></label>
                    </div>
                    <div class="col-md-8">
                        <input class="form-control" type="text" name="configName"
                               value="${keycloakProperties['configName']}"
                               <c:if test="${not empty providerKey}">disabled</c:if>/>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label">
                            <fmt:message bundle="${bundle}" key="KeycloakConfiguration.site"/>
                        </label>
                    </div>
                    <div class="col-md-8">
                        <input type="text" name="propValue.target.site" class="form-control"
                               value="${keycloakProperties['target.site']}" list="sites"/>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label">
                            <fmt:message bundle="${bundle}" key="KeycloakConfiguration.baseUrl"/>
                        </label>
                    </div>
                    <div class="col-md-8">
                        <input type="text" name="propValue.baseUrl" class="form-control"
                               value="${keycloakProperties['baseUrl']}"/>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label">
                            <fmt:message bundle="${bundle}" key="KeycloakConfiguration.realm"/>
                        </label>
                    </div>
                    <div class="col-md-8">
                        <input type="text" name="propValue.realm" class="form-control"
                               value="${keycloakProperties['realm']}"/>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label">
                            <fmt:message bundle="${bundle}" key="KeycloakConfiguration.clientId"/>
                        </label>
                    </div>
                    <div class="col-md-8">
                        <input type="text" name="propValue.clientId" class="form-control"
                               value="${keycloakProperties['clientId']}"/>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-md-4">
                        <label class="control-label">
                            <fmt:message bundle="${bundle}" key="KeycloakConfiguration.clientSecret"/>
                        </label>
                    </div>
                    <div class="col-md-8">
                        <input type="password" name="propValue.clientSecret" class="form-control"
                               value="${keycloakProperties['clientSecret']}"/>
                    </div>
                </div>
            </div>
        </fieldset>
    </div>
</div>
