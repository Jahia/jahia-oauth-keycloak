# jahia-oauth-keycloak

This module is a community module to show how to implement a Keycloak Authentication based on OAuth protocol.

## How to configure ?

### Keycloak

#### Keycloak ClientID for the authentication
In your Keycloak realm, create a new public client, for instance *jahia*.

Activate the standard flow and direct access grants.

Register valid redirect Urls and web origins.

#### Keycloak ClientID for Admin Rest API
In your Keycloak realm, create a new confidential client, for instance *jahia-admin*.

Activate the option "Use Refresh Tokens For Client Credentials Grant" in OpenID Connect Compatibility Modes. 

The service account associated with your client needs to be allowed to view the realm users.
1. Go to http://{keycloak}/auth/admin/{realm_name}/console/#/realms/{realm_name}/clients
2. Select your client (which must be a confidential client)
3. In the settings tab, switch Service Account Enabled to ON
4. Click on save, the Service Account Roles tab will appear
5. In Client Roles, select realm_management
6. Scroll through available roles until you can select view_users
7. Click on Add selected

### Jahia
Deploy modules :
* jahia-authentication-1.7.0.jar
* jahia-oauth-3.2.0.jar and enable it on your site
* jahia-oauth-keycloak-1.0.0.jar and enable it on your site

#### Jahia Authentication

Configure the Keycloak connector under site administration:
* Client ID: the public client
* Scope: optional
* Base URL: {keycloak} must not end with a slash character
* Realm: the realm
* Callback URL: *keycloakOAuthCallbackAction.do*, the Jahia OAuth callback action

You can safely delete the auto created page Authentication result.

Each protected resource checks if a user is logged or redirects to keycloak authentication.

#### Jahia User Group Provider

It is not recommended to save external user in the JCR. This module implements a custom user group provider.

The User Group Provider calls the Keycloak admin Rest API in order to search groups, users and members.

API results are cached in a specific EHCache for users and another for groups.

Configure the Keycloak user group provider under server administration:
* Name: the user group provider name
* Site: the site where users and groups are mounted, globally if empty
* Base URL: {keycloak} must not end with a slash character
* Client ID: the confidentifial client
* Client secret: the confidential client secret  
