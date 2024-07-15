/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is a factory class, which creates a <code>SecurityProvider</code>
 * instance depending on the configuration of system
 * security using EM Gui. It shields the general users from the lower
 * implementations and provides a simpler programming interface. It also
 * keeps a private set of entries, which are authenticated successfully.
 *
 * @author Helen Chen
 * @version 5.1, 9/20/2003
 */
public class SecurityEngine implements SessionListener, MessageListener, AutoCloseable {
   /**
    * Create a <code>SecurityEngine</code> object
    */
   public SecurityEngine() {
      init();
      authenticationService = AuthenticationService.getInstance();
      authenticationService.addSessionListener(this);
      clusterInstance = Cluster.getInstance();
      clusterInstance.addMessageListener(this);
   }

   /**
    * Initialize the engine. This method must be called if the
    * <code>SecurityProvider</code> is changed using EM Adm.
    */
   public void init() {
      initLock.lock();

      try {
         doInit();
      }
      finally {
         initLock.unlock();
      }
   }

   private void doInit() {
      XUtil.setXIdentityFinder(new SRIdentityFinder());

      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      if(vprovider != null) {
         vprovider.tearDown();
         vprovider = null;
      }

      vprovider = CompositeSecurityProvider.create(
         new VirtualAuthenticationProvider(), new VirtualAuthorizationProvider());
      int userCount = LicenseManager.getInstance().getNamedUserCount();

      if(isSecurityEnabled() || userCount > 0) {
         AuthenticationChain authcChain = new AuthenticationChain();
         AuthorizationChain authzChain = new AuthorizationChain();

         if(userCount > 0 && authcChain.getProviders().isEmpty()) {
            FileAuthenticationProvider authc = new FileAuthenticationProvider();
            authc.setProviderName("Primary");
            authc.removeUser(new IdentityID("guest", Organization.getDefaultOrganizationName()));
            authcChain.setProviders(Collections.singletonList(authc));
         }

         if(userCount > 0 && authzChain.getProviders().isEmpty()) {
            FileAuthorizationProvider authz = new FileAuthorizationProvider();
            authz.setProviderName("Primary");
            authzChain.setProviders(Collections.singletonList(authz));
         }

         if(!authcChain.getProviders().isEmpty() && !authzChain.getProviders().isEmpty()) {
            provider = CompositeSecurityProvider.create(authcChain, authzChain);
         }
         else {
            authcChain.tearDown();
            authzChain.tearDown();
         }

         authcChain.addAuthenticationChangeListener(authzChain);
         authcChain.addAuthenticationChangeListener(this::fireAuthenticationChange);
      }

      String classname = SreeEnv.getProperty("sree.security.listeners");

      if(classname != null && !"".equals(classname.trim())) {
         String[] names = classname.split(",");

         for(String name : names) {
            try {
               addLoginListener(
                  (LoginListener) Class.forName(
                     Tool.convertUserClassName(name)).getDeclaredConstructor().newInstance());
            }
            catch(Exception ex) {
               LOG.error("Failed to create and add log in listener: " + name, ex);
            }
         }
      }
   }

   /**
    * Get a <code>SecurityEngine</code> object.
    *
    * @return a <code>SecurityEngine</code> object
    */
   public static SecurityEngine getSecurity() {
      return SingletonManager.getInstance(SecurityEngine.class);
   }

   /**
    * Add login listener.
    */
   public void addLoginListener(LoginListener listener) {
      if(listener != null) {
         loginListeners.add(listener);
      }
   }

   /**
    * Remove login listener.
    */
   public void removeLoginListener(LoginListener listener) {
      loginListeners.remove(listener);
   }

   public void addAuthenticationChangeListener(AuthenticationChangeListener listener) {
      if(listener != null) {
         authenticationChangeListeners.add(listener);
      }
   }

   public void removeAuthenticationChangeListener(AuthenticationChangeListener listener) {
      authenticationChangeListeners.remove(listener);
   }

   private void fireAuthenticationChange(AuthenticationChangeEvent event) {
      authenticationChangeListeners.forEach(l -> l.authenticationChanged(event));
   }

   /**
    * Authenticates a user. This method has no side effects, it simply determines if the
    * supplied credentials are valid.
    *
    * @param userId     the log in of the user to authenticate.
    * @param credential the authentication credential for the user.
    * @param callback   an option callback to perform additional validation on the
    *                   authenticated user.
    *
    * @return <tt>true</tt> if the credential is authenticated successfully;
    *         <tt>false</tt> otherwise.
    */
   public static boolean authenticate(IdentityID userId, Object credential,
                                      AuthenticationCallback callback)
   {
      boolean result = false;

      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = engine.getSecurityProvider();

      if(provider.authenticate(userId, credential)) {
         result = callback == null || callback.isValid(userId, credential, provider);
      }

      return result;
   }

   public void newChain() {
      AuthenticationChain authcChain = new AuthenticationChain();
      List<AuthenticationProvider> authcProviders = authcChain.getProviderList();
      FileAuthenticationProvider authcProvider = new FileAuthenticationProvider();
      authcProvider.setProviderName("Primary");
      authcProviders.add(authcProvider);
      authcChain.setProviders(authcProviders);

      AuthorizationChain authzChain = new AuthorizationChain();
      List<AuthorizationProvider> authzProviders = authzChain.getProviderList();
      FileAuthorizationProvider authzProvider = new FileAuthorizationProvider();
      authzProvider.setProviderName("Primary");
      authzProviders.add(authzProvider);
      authzChain.setProviders(authzProviders);

      provider = CompositeSecurityProvider.create(authcChain, authzChain);
      authcChain.addAuthenticationChangeListener(authzChain);
      authcChain.addAuthenticationChangeListener(this::fireAuthenticationChange);
   }

   private void setSecurityEnabled(boolean enabled) throws IOException {
      SreeEnv.setProperty("security.enabled", enabled + "");
      SreeEnv.save();
   }

   public void enableSecurity() throws Exception {
      setSecurityEnabled(true);
      AuthenticationChain authcChain = new AuthenticationChain();
      AuthorizationChain authzChain = new AuthorizationChain();

      if(!authcChain.getProviders().isEmpty() && !authzChain.getProviders().isEmpty()) {
         provider = CompositeSecurityProvider.create(authcChain, authzChain);
         authcChain.addAuthenticationChangeListener(authzChain);
         authcChain.addAuthenticationChangeListener(this::fireAuthenticationChange);
      }
      else {
         newChain();
      }

      clusterInstance.sendMessage(new SecurityChangedMessage(true));
   }

   public void disableSecurity() throws Exception {
      setSecurityEnabled(false);

      if(provider != null) {
         provider.tearDown();
      }

      clusterInstance.sendMessage(new SecurityChangedMessage(false));
   }

   public void enableSelfSignup() throws Exception {
      setSelfSignupEnabled(true);
   }

   public void disableSelfSignup() throws Exception {
      setSelfSignupEnabled(false);
   }

   public boolean isSelfSignupEnabled() {
      return isSecurityEnabled() &&
         "true".equals(SreeEnv.getProperty("security.selfSignUp.enabled"));
   }

   private void setSelfSignupEnabled(boolean enabled) throws IOException {
      SreeEnv.setProperty("security.selfSignUp.enabled", enabled + "");
      SreeEnv.save();
   }

   @Override
   public void close() throws Exception {
      if(provider != null) {
         provider.tearDown();
      }

      if(authenticationService != null) {
         authenticationService.removeSessionListener(this);
      }

      if(clusterInstance != null) {
         clusterInstance.removeMessageListener(this);
      }
   }

   public static void clear() {
      SingletonManager.reset(SecurityEngine.class);
   }

   /**
    * It checks the authentication of specific entity. If the authentication
    * succeeds, the entity will be saved.
    *
    * @param user       the client info of the user to be authenticated.
    * @param credential it wraps up the some secure message, such as user
    *                   id and password
    * @return a <code>Principal</code> Object if no security provider is set
    *         or the authentication checking is succeed or <code>null</code> if the
    *         authentication checking fails.
    */
   Principal authenticate(ClientInfo user, Object credential) {
      return authenticate(user, credential, provider);
   }

   /**
    * It checks the authentication of specific entity. If the authentication
    * succeeds, the entity will be saved.
    *
    * @param user       the client info of the user to be authenticated.
    * @param credential it wraps up the some secure message, such as user
    *                   id and password
    * @param provider   the security provider.
    *
    * @return a <code>Principal</code> Object if no security provider is set
    *         or the authentication checking is succeed or <code>null</code> if the
    *         authentication checking fails.
    */
   Principal authenticate(ClientInfo user, Object credential, SecurityProvider provider) {
      SRPrincipal principal = null;

      if(provider == null ||
         ClientInfo.ANONYMOUS.equals(user.getLoginUserID().name) &&
         provider.getAuthenticationProvider().isVirtual())
      {
         if(user == null) {
            String addr = null;

            try {
               addr = Tool.getIP();
            }
            catch(Exception e) {
               LOG.warn("Failed to get local IP address for user", e);
            }

            principal = new DestinationUserNameProviderPrincipal(
               new ClientInfo(new IdentityID(ClientInfo.ANONYMOUS, addr),Organization.getDefaultOrganizationName()
               ), new IdentityID[0], new String[0],Organization.getDefaultOrganizationID(), 0L);
            principal.setProperty("__internal__", "true");
         }
         else {
            principal =
               new DestinationUserNameProviderPrincipal(user, new IdentityID[0], new String[0],
                                                   Organization.getDefaultOrganizationID(), 0L);
            principal.setProperty("__internal__", "true");
         }
      }
      else if(ClientInfo.ANONYMOUS.equals(user.getLoginUserID().name)) {
         if(containsAnonymous()) {
            principal = users.get(user);

            if(principal == null ||
               ((new Date()).getTime() - principal.getAge()) > 36000000L) {
               long secureID = SecurityEngine.getRandom().nextLong();

               synchronized(this) {
                  principal = new DestinationUserNameProviderPrincipal(
                     user, provider.getRoles(user.getUserIdentity()),
                     provider.getUserGroups(user.getUserIdentity()),
                     provider.getOrgId((provider.getUser(user.getUserIdentity()).getOrganization())),
                     secureID);
                  principal.setProperty("__internal__", "true");
                  users.remove(user);
                  principal.setProperty("login.user", "true");
                  users.put(user, principal);
                  SUtil.setAdditionalDatasource(principal);
               }
            }
         }
      }
      else if(credential instanceof SRPrincipal ||
              provider.authenticate(user.getLoginUserID(), credential))
      {
         synchronized(this) {
            boolean internal = !(credential instanceof SRPrincipal);
            // @by stephenwebster, For Bug #6240
            // After authentication, get the User object to associate the loginid
            // with the correct userid in the security provider.  Currently,
            // only LDAP seems to allow authenticating a user case-insensitively.
            User realUser = provider.getUser(user.getUserIdentity());

            if(realUser != null) {
               user.setUserName(realUser.getIdentityID());
            }

            principal = users.get(user);

            if(principal == null ||
               ((new Date()).getTime() - principal.getAge()) > 36000000L)
            {
               long secureID = SecurityEngine.getRandom().nextLong();
               principal = !internal ? (SRPrincipal) credential :
                  new DestinationUserNameProviderPrincipal(
                     user, provider.getRoles(user.getUserIdentity()),
                     provider.getUserGroups(user.getUserIdentity()),
                     realUser != null ? provider.getOrgId(realUser.getOrganization()) :
                        Organization.getDefaultOrganizationID(),
                     secureID,  realUser != null ? realUser.getAlias() : null);

               if(internal) {
                  principal.setProperty("__internal__", "true");
               }

               users.remove(user);
               principal.setProperty("login.user", "true");
               users.put(user, principal);
            }
         }
      }

      fireLoginEvent(principal);
      return principal;
   }

   /**
    * Check if contains user anonymous.
    */
   public boolean containsAnonymous() {
      if(provider == null) {
         return false;
      }

      return provider.containsAnonymousUser();
   }

   /**
    * Fire login event.
    */
   public void fireLoginEvent(SRPrincipal user) {
      if(user == null) {
         return;
      }

      try {
         LoginEvent event = new LoginEvent(user);

         for(LoginListener listener : loginListeners) {
            listener.userLogin(event);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to handle log in event for user: " + user, ex);
      }
   }

   /**
    * Log the user out of the system.
    *
    * @param principal it represents an entity
    */
   private void logout(Principal principal) {
      if((principal instanceof SRPrincipal) && isLogin(principal)) {
         synchronized(this) {
            users.remove(((SRPrincipal) principal).getUser());
         }
      }
   }

   /**
    * Get a list of all users in the system.
    *
    * @return list of users.
    */
   public IdentityID[] getUsers() {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getUsers();
   }

   /**
    * Get a list of all users in a group.
    *
    * @return list of users
    */
   public IdentityID[] getUsers(IdentityID group) {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getUsers(group);
   }

   /**
    * Get a list of all users in an organization.
    *
    * @return list of users
    */
   public IdentityID[] getOrgUsers(String orgID) {
      if(provider == null) {
         return new IdentityID[0];
      }

      return Arrays.stream(provider.getUsers())
         .filter(user -> provider.getOrgId(user.organization)
            .equals(orgID))
         .toArray(IdentityID[]::new);
   }

   /**
    * Get a list of all users not in any group except INDIVIDUAL.
    *
    * @return list of users.
    */
   public IdentityID[] getIndividualUsers() {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getIndividualUsers();
   }

   /**
    * Get a list of all emails for a user.
    *
    * @return list of emails.
    */
   @SuppressWarnings("deprecation")
   public String[] getEmails(IdentityID user) {
      if(provider == null) {
         return new String[0];
      }

      return provider.getEmails(user);
   }

   /**
    * Get the user id from the principal.
    *
    * @param principal it represents an entity
    * @return the user id
    */
   public String getUser(Principal principal) {
      return principal.getName();
   }

   /**
    * Get a list of all groups defined in the system. If groups are nested,
    * only the top level groups should be returned.
    *
    * @return a list of groups.
    */
   public IdentityID[] getGroups() {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getGroups();
   }

   /**
    * Gets a list of all groups bound to the specified user.
    *
    * @param user the name of the user.
    *
    * @return a list of group names.
    */
   public String[] getUserGroups(IdentityID user) {
      if(provider == null) {
         return new String[0];
      }

      return provider.getUserGroups(user);
   }

   /**
    * Get a list of all organizations in the system.
    *
    * @return list of organizations.
    */
   public String[] getOrganizations() {
      if(provider == null) {
         return new String[0];
      }

      return provider.getOrganizations();
   }

   /**
    * Get a list of all roles bound to a specific user.
    *
    * @param user user id
    * @return a list of roles.
    */
   public IdentityID[] getRoles(IdentityID user) {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getRoles(user);
   }

   /**
    * Get a list of all roles in the system.
    *
    * @return a list of roles.
    */
   public IdentityID[] getRoles() {
      if(provider == null) {
         return new IdentityID[0];
      }

      return provider.getRoles();
   }

   /**
    * Get a list of all roles in the system.
    *
    * @return a list of roles.
    */
   public Role[] getRolesOrgScoped(boolean siteAdmin) {
      if(provider == null) {
         return new Role[0];
      }

      String orgName = OrganizationManager.getCurrentOrgName();
      AuthenticationProvider authenticationProvider = provider.getAuthenticationProvider();

      if(authenticationProvider instanceof AuthenticationChain) {
         for(AuthenticationProvider p : ((AuthenticationChain) authenticationProvider).getProviders()) {
            if(p.getOrganization(orgName) != null) {
               authenticationProvider = p;
               break;
            }
         }
      }

      AuthenticationProvider currentProvider = authenticationProvider;

      return Arrays.stream(currentProvider.getRoles())
         .map(r -> currentProvider.getRole(r))
         .filter(r -> siteAdmin && r.getOrganization() == null ||
            !siteAdmin && currentProvider.isOrgAdministratorRole(r.getIdentityID()) ||
            r.getOrganization() != null && r.getOrganization().equals(orgName))
         .toArray(Role[]::new);
   }

   /**
    * Get a list the email addresses of users that do not belong to any group.
    *
    * @return a list of email addresses.
    */
   public String[] getIndividualEmailAddresses() {
      if(provider == null) {
         return new String[0];
      }

      return provider.getAuthenticationProvider().getIndividualEmailAddresses();
   }

   /**
    * Set the permission for a specific resource. It is ignored if no security
    * is provided.
    *
    * @param resource resource name, such as a replet path or a saved
    *                 report path.
    * @param perm     permission setting.
    */
   public void setPermission(ResourceType type, String resource, Permission perm) {
      if(isSecurityEnabled() && provider != null) {
         if(isBlank(perm)) {
            removePermission(type, resource);
         }
         else {
            provider.setPermission(type, resource, perm);
         }
      }
   }

   /**
    * Remove the user permission. It is ignored if no security is provided.
    *
    * @param resource resource name, such as a replet registry name or a saved
    *                 report path.
    */
   public void removePermission(ResourceType type, String resource) {
      if(isSecurityEnabled() && provider != null) {
         provider.removePermission(type, resource);
      }
   }

   /**
    * Get the permission for the specified resource.
    *
    * @param resource resource name.
    * @return permission setting or <code>null</code> if no permission is set
    *         for this resource.
    */
   public Permission getPermission(ResourceType type, String resource) {
      if(isSecurityEnabled() && provider != null) {
         return provider.getPermission(type, resource);
      }

      return null;
   }

   public Permission getPermission(ResourceType type, IdentityID resource) {
      if(isSecurityEnabled() && provider != null) {
         return provider.getPermission(type, resource);
      }

      return null;
   }

   /**
    * Check the permission to access a resource. It always returns true if no
    * security is provided.
    *
    * @param principal it represents an entity
    * @param type      the type of resource.
    * @param resource  resource name.
    * @param action    the permitted action.
    *
    * @return true if the permission is granted to this principal
    *
    * @throws SecurityException if the principal did not login
    */
   public boolean checkPermission(final Principal principal, final ResourceType type,
                                  final String resource, final ResourceAction action)
      throws SecurityException
   {
      boolean vpm = false;

      // for vpm user which setted by composer vpm principal dialog.
      if(principal instanceof SRPrincipal &&
         "true".equals(((SRPrincipal) principal).getProperty(SUtil.VPM_USER)))
      {
         vpm = true;
      }

      SecurityProvider provider = vpm ? getVpmSecurityProvider() : getSecurityProvider();

      if(provider == null) {
         return true;
      }

      if(principal == null) {
         return false;
      }

      // in EE, admin needs to manage the resources in user scope.
      // To access the resource, admin will create principal to fetch
      // resource properly. The principal is not authenticated. To support
      // this usage, here we ignore the authentication check
      if(type != ResourceType.MY_DASHBOARDS && !isLogin(principal)) {
         throw new SecurityException(principal.getName() + " did not login.");
      }

      boolean datasource = "true".equals(securityDatasourceEveryone.get());
      boolean script = "true".equals(securityScriptEveryone.get());;
      boolean tablestyle = "true".equals(securityTablestyleEveryone.get());
      boolean scheduletask = "true".equals(securitySchduletaskEveryone.get());
      boolean allowed = provider.checkPermission(principal, type, resource, action);

      if(!allowed && action != ResourceAction.ADMIN &&
         !(action == ResourceAction.READ && (type == ResourceType.DATA_SOURCE ||
            type == ResourceType.DATA_SOURCE_FOLDER) && resource.contains("::")))
      {
         // inheritance rules are different for admin, check that too
         allowed = provider.checkPermission(principal, type, resource, ResourceAction.ADMIN);
      }

      if(!allowed) {
         if(type == ResourceType.CHART_TYPE) {
            Permission perm = getPermission(type, resource);

            if(perm == null) {
               Resource parent = type.getParent(resource);

               if(parent != null) {
                  allowed = checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
            }
         }
         else if(script && (type == ResourceType.SCRIPT || type == ResourceType.SCRIPT_LIBRARY) ||
            tablestyle && (type == ResourceType.TABLE_STYLE || type == ResourceType.TABLE_STYLE_LIBRARY))
         {
            Permission perm = getPermission(type, resource);

            if(perm == null) {
               final Resource parent = type.getParent(resource);

               if(type == ResourceType.SCRIPT_LIBRARY || type == ResourceType.TABLE_STYLE_LIBRARY) {
                  final Permission parentPerm = getPermission(parent.getType(), parent.getPath());

                  if(isBlank(parentPerm)) {
                     allowed = action == ResourceAction.READ;
                  }
                  else {
                     allowed = checkPermission(principal, parent.getType(), parent.getPath(),
                                               action);
                  }
               }
               else {
                  allowed = checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
            }
            else {
               allowed = isBlank(perm) && action == ResourceAction.READ;
            }
         }
         else if(scheduletask && type == ResourceType.SCHEDULE_TASK_FOLDER) {
            Permission perm = getPermission(type, resource);

            if(isBlank(perm) && type.isHierarchical()) {
               Resource parent = type.getParent(resource);

               if(parent != null) {
                  allowed =
                     checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
               else {
                  perm = getPermission(ResourceType.SCHEDULE_TASK_FOLDER, "/");
                  allowed = isBlank(perm) ? action.equals(ResourceAction.READ) :
                     checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER, "/", action);
               }
            }
         }
         else if(datasource && (type == ResourceType.QUERY_FOLDER ||
            type == ResourceType.DATA_SOURCE || type == ResourceType.DATA_SOURCE_FOLDER ||
            type == ResourceType.DATA_MODEL_FOLDER || type == ResourceType.QUERY ||
            type == ResourceType.CUBE))
         {
            if(type == ResourceType.QUERY_FOLDER) {
               if(action == ResourceAction.READ) {
                  Permission perm = getPermission(type, resource);
                  allowed = isBlank(perm);
               }
            }
            else if(type == ResourceType.DATA_SOURCE || type == ResourceType.DATA_SOURCE_FOLDER ||
               type == ResourceType.DATA_MODEL_FOLDER)
            {
               Permission perm = getPermission(type, resource);

               if(isBlank(perm) && type.isHierarchical()) {
                  Resource parent = type.getParent(resource);

                  if(parent != null) {
                     allowed =
                        checkPermission(principal, parent.getType(), parent.getPath(), action);
                  }
                  else {
                     perm = getPermission(ResourceType.DATA_SOURCE_FOLDER, "/");
                     allowed = isBlank(perm) ?
                        action.equals(ResourceAction.READ) :
                        checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER, "/", action);
                  }
               }
            }
            else if(type == ResourceType.QUERY || type == ResourceType.CUBE) {
               Permission perm = getPermission(type, resource);

               if(isBlank(perm) && type.isHierarchical()) {
                  Resource parent = type.getParent(resource);

                  if(parent != null) {
                     allowed =
                        checkPermission(principal, parent.getType(), parent.getPath(), action);
                  }
               }
            }
         }
      }

      return allowed;
   }

   /**
    * Check the permission to access a resource. It always returns true if no
    * security is provided.
    *
    * @param principal  it represents an entity
    * @param type       the type of resource.
    * @param identityID resource name.
    * @param action     the permitted action.
    *
    * @return true if the permission is granted to this principal
    *
    * @throws SecurityException if the principal did not login
    */
   public boolean checkPermission(final Principal principal, final ResourceType type,
                                  final IdentityID identityID, final ResourceAction action)
      throws SecurityException
   {
      boolean vpm = false;

      // for vpm user which setted by composer vpm principal dialog.
      if(principal instanceof SRPrincipal &&
         "true".equals(((SRPrincipal) principal).getProperty(SUtil.VPM_USER)))
      {
         vpm = true;
      }

      SecurityProvider provider = vpm ? getVpmSecurityProvider() : getSecurityProvider();

      if(provider == null) {
         return true;
      }

      if(principal == null) {
         return false;
      }

      // in EE, admin needs to manage the resources in user scope.
      // To access the resource, admin will create principal to fetch
      // resource properly. The principal is not authenticated. To support
      // this usage, here we ignore the authentication check
      if(type != ResourceType.MY_DASHBOARDS && !isLogin(principal)) {
         throw new SecurityException(principal.getName() + " did not login.");
      }

      boolean datasource = "true".equals(securityDatasourceEveryone.get());
      boolean script = "true".equals(securityScriptEveryone.get());;
      boolean tablestyle = "true".equals(securityTablestyleEveryone.get());
      boolean scheduletask = "true".equals(securitySchduletaskEveryone.get());
      boolean allowed = provider.checkPermission(principal, type, identityID.convertToKey(), action);

      if(!allowed && action != ResourceAction.ADMIN &&
         !(action == ResourceAction.READ && (type == ResourceType.DATA_SOURCE ||
            type == ResourceType.DATA_SOURCE_FOLDER) && identityID.name.contains("::")))
      {
         // inheritance rules are different for admin, check that too
         allowed = provider.checkPermission(principal, type, identityID.convertToKey(), ResourceAction.ADMIN);
      }

      if(!allowed) {
         if(type == ResourceType.CHART_TYPE) {
            Permission perm = getPermission(type, identityID);

            if(perm == null) {
               Resource parent = type.getParent(identityID.convertToKey());

               if(parent != null) {
                  allowed = checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
            }
         }
         else if(script && (type == ResourceType.SCRIPT || type == ResourceType.SCRIPT_LIBRARY) ||
            tablestyle && (type == ResourceType.TABLE_STYLE || type == ResourceType.TABLE_STYLE_LIBRARY))
         {
            Permission perm = getPermission(type, identityID);

            if(perm == null) {
               final Resource parent = type.getParent(identityID.convertToKey());

               if(type == ResourceType.SCRIPT_LIBRARY || type == ResourceType.TABLE_STYLE_LIBRARY) {
                  final Permission parentPerm = getPermission(parent.getType(), parent.getPath());

                  if(isBlank(parentPerm)) {
                     allowed = action == ResourceAction.READ;
                  }
                  else {
                     allowed = checkPermission(principal, parent.getType(), parent.getPath(),
                                               action);
                  }
               }
               else {
                  allowed = checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
            }
            else {
               allowed = isBlank(perm) && action == ResourceAction.READ;
            }
         }
         else if(scheduletask && type == ResourceType.SCHEDULE_TASK_FOLDER) {
            Permission perm = getPermission(type, identityID);

            if(isBlank(perm) && type.isHierarchical()) {
               Resource parent = type.getParent(identityID.convertToKey());

               if(parent != null) {
                  allowed =
                     checkPermission(principal, parent.getType(), parent.getPath(), action);
               }
               else {
                  perm = getPermission(ResourceType.SCHEDULE_TASK_FOLDER, "/");
                  allowed = isBlank(perm) ? action.equals(ResourceAction.READ) :
                     checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER, "/", action);
               }
            }
         }
         else if(datasource && (type == ResourceType.QUERY_FOLDER ||
            type == ResourceType.DATA_SOURCE || type == ResourceType.DATA_SOURCE_FOLDER ||
            type == ResourceType.DATA_MODEL_FOLDER || type == ResourceType.QUERY ||
            type == ResourceType.CUBE))
         {
            if(type == ResourceType.QUERY_FOLDER) {
               if(action == ResourceAction.READ) {
                  Permission perm = getPermission(type, identityID);
                  allowed = isBlank(perm);
               }
            }
            else if(type == ResourceType.DATA_SOURCE || type == ResourceType.DATA_SOURCE_FOLDER ||
               type == ResourceType.DATA_MODEL_FOLDER)
            {
               Permission perm = getPermission(type, identityID);

               if(isBlank(perm) && type.isHierarchical()) {
                  Resource parent = type.getParent(identityID.convertToKey());

                  if(parent != null) {
                     allowed =
                        checkPermission(principal, parent.getType(), parent.getPath(), action);
                  }
                  else {
                     perm = getPermission(ResourceType.DATA_SOURCE_FOLDER, "/");
                     boolean isSelfAndNotAdmin = Tool.equals(IdentityID.getIdentityIDFromKey(principal.getName()).organization,
                                                               Organization.getSelfOrganizationName()) &&
                                                !OrganizationManager.getInstance().isSiteAdmin(principal) &&
                                                !OrganizationManager.getInstance().isOrgAdmin(principal);
                     allowed = isBlank(perm) ?
                        action.equals(ResourceAction.READ) && !isSelfAndNotAdmin  :
                        checkPermission(principal, ResourceType.DATA_SOURCE_FOLDER, "/", action);
                  }
               }
            }
            else if(type == ResourceType.QUERY || type == ResourceType.CUBE) {
               Permission perm = getPermission(type, identityID);

               if(isBlank(perm) && type.isHierarchical()) {
                  Resource parent = type.getParent(identityID.convertToKey());

                  if(parent != null) {
                     allowed =
                        checkPermission(principal, parent.getType(), parent.getPath(), action);
                  }
               }
            }
         }
      }

      return allowed;
   }

   /**
    * Change the password for an entity. It is supported only on
    * the security realms with password.
    *
    * @param principal it represents an entity
    * @param password  the new password
    * @throws SRSecurityException if changing password failed.
    */
   public void changePassword(Principal principal, String password)
      throws SRSecurityException
   {
      if(isSecurityEnabled()) {
         if(!isLogin(principal)) {
            String user = (principal == null) ?
               "This user" :
               principal.getName();

            throw new SRSecurityException(user + " did not login.");
         }

         EditableAuthenticationProvider auth =
            SUtil.getEditableAuthenticationProvider(provider);

         if(auth != null) {
            auth.changePassword(IdentityID.getIdentityIDFromKey(principal.getName()), password);
         }
         else {
            throw new SRSecurityException("Security provider is not editable," +
               "can not change password.");
         }
      }
      else {
         FSUser admin = (FSUser) vprovider.getUser(new IdentityID("admin", Organization.getDefaultOrganizationName()));
         SUtil.setPassword(admin, password);
         VirtualAuthenticationProvider vaprovider =
            (VirtualAuthenticationProvider) vprovider.getAuthenticationProvider();
         vaprovider.addUser(admin);
      }
   }

   /**
    * Get the <code>SecurityProvider</code> object used by this system.
    *
    * @return a <code>SecurityProvider</code> instance or <code>null</code>
    *         if no security provided is defined
    */
   public SecurityProvider getSecurityProvider() {
      return isSecurityEnabled() && provider != null ? provider : vprovider;
   }

   /**
    * For studio composer which setted vpm user by vpm principal dialog.
    * @return
    */
   public SecurityProvider getVpmSecurityProvider() {
      if(isSecurityEnabled()) {
         return vpm_provider != null ? vpm_provider : provider != null ? provider : vprovider;
      }

      return vprovider;
   }

   public boolean isSecurityEnabled() {
      return "true".equals(SreeEnv.getProperty("security.enabled"));
   }

   /**
    * Get the <code>AuthenticationChain</code> object used by this system.
    *
    * @return a <code>AuthenticationChain</code> instance
    */
   public Optional<AuthenticationChain> getAuthenticationChain() {
      AuthenticationProvider authcProvider = getSecurityProvider().getAuthenticationProvider();

      if(authcProvider instanceof AuthenticationChain) {
         return Optional.of((AuthenticationChain) authcProvider);
      }

      return Optional.empty();
   }

   /**
    * Get the <code>AuthorizationChain</code> object used by this system.
    *
    * @return a <code>AuthorizationChain</code> instance
    */
   public Optional<AuthorizationChain> getAuthorizationChain() {
      AuthorizationProvider authzProvider = getSecurityProvider().getAuthorizationProvider();

      if(authzProvider instanceof AuthorizationChain) {
         return Optional.of((AuthorizationChain) authzProvider);
      }

      return Optional.empty();
   }

   /**
    * Get sub nodes of security tree.
    * For better performance, user does not contain email, groups, roles, etc.
    */
   public IdentityNode[] getSubIdentities(IdentityNode node) {
      return getSubIdentities(node, null, null, null, true, true, true);
   }

   /**
    * Get sub nodes of security tree.
    * For better performance, user does not contain email, groups, roles, etc.
    */
   public IdentityNode[] getSubIdentities(IdentityNode node,
                                          String userFilter, String groupFilter, String roleFilter,
                                          boolean showUsers, boolean showGroups, boolean showRoles) {
      int type = node.getType();
      IdentityNode[] nodes = new IdentityNode[0];

      if(provider == null) {
         return nodes;
      }

      if(IdentityNode.ROOT == type) {
         Catalog catalog = Catalog.getCatalog();
         ArrayList<IdentityNode> result = new ArrayList<>();

         if((showUsers || showGroups) && roleFilter == null) {
            IdentityID subIdentity = new IdentityID(catalog.getString("Users"), node.getIdentityID().organization);
            result.add(new IdentityNode(subIdentity, IdentityNode.USERS, false));
         }

         if(showRoles && userFilter == null && groupFilter == null) {
            IdentityID subIdentity = new IdentityID(catalog.getString("Roles"), node.getIdentityID().organization);
            result.add(new IdentityNode(subIdentity, IdentityNode.ROLES, false));
         }

         nodes = result.toArray(new IdentityNode[0]);
      }
      else if(IdentityNode.USERS == type) {
         ArrayList<IdentityNode> list = new ArrayList<>();

         // do not show groups if search users
         if(showGroups && userFilter == null) {
            IdentityID[] groups = provider.getGroups();
            groups = (groups == null) ? new IdentityID[0] : groups;
            Arrays.sort(groups);

            for(IdentityID groupID : groups) {
               if(groupFilter != null &&
                  !groupID.name.toLowerCase().contains(groupFilter.toLowerCase()))
               {
                  continue;
               }

               Group group = provider.getGroup(groupID);

               if(group == null) {
                  continue;
               }

               if(groupFilter != null) {
                  list.add(new IdentityNode(group));
               }

               if(groupFilter == null && group.getGroups().length == 0) {
                  list.add(new IdentityNode(group));
               }
            }
         }

         if(showUsers && groupFilter == null) {
            if(userFilter == null && showGroups) {
               IdentityID[] users = provider.getUsers();
               users = (users == null) ? new IdentityID[0] : users;
               Arrays.sort(users);

               for(IdentityID userName : users) {
                  User user = new User(userName);
                  list.add(new IdentityNode(user));
               }
            }
            else if(userFilter != null) {
               IdentityID[] users = provider.getUsers();
               users = (users == null) ? new IdentityID[0] : users;
               Arrays.sort(users);

               for(IdentityID userID : users) {
                  if(userID.name.toLowerCase().contains(userFilter.toLowerCase())) {
                     User user = new User(userID);

                     // add all matched users if search users
                     list.add(new IdentityNode(user));
                  }
               }
            }
         }

         nodes = new IdentityNode[list.size()];
         list.toArray(nodes);
      }
      else if(IdentityNode.ROLES == type) {
         ArrayList<IdentityNode> list = new ArrayList<>();
         IdentityID[] roles = provider.getRoles();
         roles = (roles == null) ? new IdentityID[0] : roles;

         Arrays.sort(roles);

         for(IdentityID roleID : roles) {
            if(roleFilter == null ||
               roleID.name.toLowerCase().contains(roleFilter.toLowerCase()))
            {
               Role role = provider.getRole(roleID);

               if(role != null) {
                  if(roleFilter != null) {
                     list.add(new IdentityNode(role));
                  }

                  if(roleFilter == null && role.getRoles().length == 0) {
                     list.add(new IdentityNode(role));
                  }
               }
            }
         }

         nodes = new IdentityNode[list.size()];
         list.toArray(nodes);
      }
      else if(type == Identity.GROUP) {
         ArrayList<IdentityNode> list = new ArrayList<>();
         IdentityID[] groups = provider.getGroups();
         groups = (groups == null) ? new IdentityID[0] : groups;
         Arrays.sort(groups);

         for(IdentityID groupID : groups) {
            if(groupFilter == null ||
               groupID.name.toLowerCase().contains(groupFilter.toLowerCase()))
            {
               Group group = provider.getGroup(groupID);

               if(group != null) {
                  String[] pgroups = group.getGroups();

                  for(String pgroup : pgroups) {
                     if(pgroup.equals(node.getIdentityID())) {
                        list.add(new IdentityNode(group));
                        break;
                     }
                  }
               }
            }
         }

         if(showUsers && groupFilter == null) {
            IdentityID[] users = provider.getUsers(node.getIdentityID());
            Arrays.sort(users);

            for(IdentityID userID : users) {
               if(userFilter == null ||
                  userID.name.toLowerCase().contains(userFilter.toLowerCase()))
               {
                  User user = new User(userID);
                  list.add(new IdentityNode(user));
               }
            }
         }

         nodes = new IdentityNode[list.size()];
         list.toArray(nodes);
      }
      else if(type == Identity.ROLE) {
         IdentityID[] roles = provider.getRoles();
         roles = (roles == null) ? new IdentityID[0] : roles;

         Arrays.sort(roles);
         ArrayList<IdentityNode> list = new ArrayList<>();

         for(IdentityID roleID : roles) {
            if(roleFilter == null ||
               roleID.name.toLowerCase().contains(roleFilter.toLowerCase()))
            {
               Role role = provider.getRole(roleID);

               if(role == null) {
                  continue;
               }

               IdentityID[] proles = role.getRoles();

               for(IdentityID prole : proles) {
                  if(prole.equals(node.getIdentityID())) {
                     list.add(new IdentityNode(role));
                     break;
                  }
               }
            }
         }

         nodes = new IdentityNode[list.size()];
         list.toArray(nodes);
      }

      return nodes;
   }

   /**
    * Check whether this user has logged in or not.
    */
   private boolean isLogin(Principal principal) {
      if(principal == null) {
         return false;
      }

      if(principal instanceof SRPrincipal) {
         SRPrincipal srPrincipal = (SRPrincipal) principal;

         if(srPrincipal.isIgnoreLogin()) {
            return true;
         }

         SRPrincipal srPrincipal2 = users.get(srPrincipal.getUser());

         // if the principal is created on the same machine as the server,
         // trust it as is so the principal can be passed from another
         // application to the report server by declaring a user
         if(srPrincipal2 == null) {
            String host2 = srPrincipal.getHost();
            String host = Tool.getIP();
            return host != null && host.equals(host2);
         }

         ClientInfo user1 = srPrincipal.getUser();
         ClientInfo user2 = srPrincipal2.getUser();

         // @by davidd bug1327353214292, checkPermission calls this method
         // and if the same user logs in twice then isLogin will return true
         // for one and false for the other causing an exception.
         // I determined that the "users" hashmap is accessed via client info
         // but SRPrincipal.equals differentiates principals with different
         // secureIDs. The customer use-case involved SSO and the ClusterServlet
         // The immediate fix for this is to compare the ClientInfo, instead of
         // using the SRPrincipal.equals method.
         if(user1 != null) {
            return user1.equals(user2);
         }

         return srPrincipal2.equals(principal);
      }

      return false;
   }

   /**
    * Determines if the specified principal has been authenticated and has an
    * active session.
    *
    * @param principal the principal to check.
    *
    * @return <tt>true</tt> if active; <tt>false</tt> otherwise.
    */
   public boolean isActiveUser(Principal principal) {
      return (principal instanceof SRPrincipal) &&
         principal.equals(users.get(((SRPrincipal) principal).getUser()));
   }

   /**
    * Check whether this user has logged in or not.
    */
   public boolean isValidUser(Principal principal) {
      if(principal == null) {
         return false;
      }

      if(principal instanceof SRPrincipal) {
         SRPrincipal sr = (SRPrincipal) principal;

         return !"true".equals(sr.getProperty("login.user")) ||
            principal.equals(users.get(sr.getUser()));
      }

      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SecurityEngine.class);

   /**
    * Check if the specified identity is valid or not.
    */
   public boolean isValidIdentity(Identity identity) {
      if(provider == null) {
         return false;
      }

      Identity id = null;

      switch(identity.getType()) {
      case Identity.GROUP:
         id = provider.getGroup(identity.getIdentityID());
         break;
      case Identity.ROLE:
         id = provider.getRole(identity.getIdentityID());
         break;
      case Identity.USER:
         id = provider.getUser(identity.getIdentityID());
         break;
      }

      return id != null;
   }

   /**
    * Get the active Principal list.
    */
   public List<SRPrincipal> getActivePrincipalList() {
      List<SRPrincipal> list = null;

      synchronized(this) {
         list = new ArrayList<>(users.values());
      }

      int len = list.size();

      for(int i = len - 1; i >= 0; i--) {
         SRPrincipal user = list.get(i);

         if(!user.isValid()) {
            list.remove(i);
         }
      }

      return list;
   }

   /**
    * Get the random number generator for principals.
    */
   public static Random getRandom() {
      return Tool.getSecureRandom();
   }

   /**
    * Gets the date and time at which the security settings were last modified.
    *
    * @return the modification time stamp.
    */
   public static long getLastModified() {
      touchLock.lock();

      try {
         return getTouchFile().lastModified();
      }
      finally {
         touchLock.unlock();
      }
   }

   /**
    * Updates the modification time stamp to the current date and time.
    *
    * @see #getLastModified()
    */
   public static void touch() {
      touchLock.lock();

      try {
         if(!getTouchFile().setLastModified(System.currentTimeMillis())) {
            LOG.warn("Failed to update last modified time of file: " + getTouchFile());
         }
      }
      finally {
         touchLock.unlock();
      }
   }

   private static File getTouchFile() {
      File file = FileSystemService.getInstance().getCacheFile("securityUpdate");

      if(!file.exists()) {
         try {
            if(!file.createNewFile()) {
               LOG.error("Failed to create security update file");
            }
         }
         catch(Exception exc) {
            LOG.error("Failed to create security update file", exc);
         }
      }

      return file;
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // NO-OP
   }

   @Override
   public void loggedOut(SessionEvent event) {
      logout(event.getPrincipal());
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(!event.isLocal() && (event.getMessage() instanceof SecurityChangedMessage)) {
         try {
            boolean enabled = ((SecurityChangedMessage) event.getMessage()).isEnabled();

            if(enabled && !isSecurityEnabled()) {
               enableSecurity();
            }
            else if(!enabled && isSecurityEnabled()) {
               disableSecurity();
            }
         }
         catch(Exception e) {
            LOG.error("Failed to update security", e);
         }
      }
   }

   public static void updateSecurityDatasourceEveryoneValue() {
      securityDatasourceEveryone.updateValue();
   }

   public static void updateSecurityScriptEveryoneValue() {
      securityScriptEveryone.updateValue();
   }

   public static void updateSecurityTablestyleEveryoneValue() {
      securityTablestyleEveryone.updateValue();
   }

   public static void updateSecuritySchduletaskEveryoneValue() {
      securitySchduletaskEveryone.updateValue();
   }

   private static boolean isBlank(Permission permission) {
      return permission == null || permission.isBlank();
   }

   private static final Lock touchLock = new ReentrantLock();
   private SecurityProvider provider = null;
   private SecurityProvider vprovider = null;
   private SecurityProvider vpm_provider = null;
   private Map<ClientInfo, SRPrincipal> users = new ConcurrentHashMap<>();
   private final Set<LoginListener> loginListeners = new LinkedHashSet<>();
   private final Set<AuthenticationChangeListener> authenticationChangeListeners =
      new LinkedHashSet<>();
   private final Lock initLock = new ReentrantLock();
   private final AuthenticationService authenticationService;
   private final Cluster clusterInstance;
   private static final SreeEnv.Value securityDatasourceEveryone =
      new SreeEnv.Value("security.datasource.everyone", 10000, "true");
   private static final SreeEnv.Value securityScriptEveryone =
      new SreeEnv.Value("security.script.everyone", 10000, "true");
   private static final SreeEnv.Value securityTablestyleEveryone =
      new SreeEnv.Value("security.tablestyle.everyone", 10000, "true");
   private static final SreeEnv.Value securitySchduletaskEveryone =
      new SreeEnv.Value("security.scheduletask.everyone", 10000, "true");

   /**
    * Interface used to perform additional authentication checks.
    */
   public interface AuthenticationCallback {
      /**
       * Determines if the specified user is valid for this context. The user ID and
       * credential have already been successfully authenticated with the security
       * provider.
       *
       * @param userId     the user ID.
       * @param credential the user's credential.
       * @param provider   the security provider.
       *
       * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
       */
      boolean isValid(IdentityID userId, Object credential, SecurityProvider provider);
   }
}
