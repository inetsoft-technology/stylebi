/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.sree.security.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataChangeListener;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.*;
import javax.naming.directory.SearchControls;
import javax.naming.ldap.LdapContext;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Base class for authentication modules that retrieve information from an LDAP
 * directory server.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
@SuppressWarnings("WeakerAccess")
public abstract class LdapAuthenticationProvider
   extends AbstractAuthenticationProvider
{
   /**
    * Creates a new instance of <tt>LdapAuthenticationProvider</tt>.
    */
   protected LdapAuthenticationProvider() {
      try {
         this.contextPool = new ContextPool(this);
         this.client = new LdapAuthenticationClient(this);
      }
      catch(Exception exc) {
         throw new RuntimeException("Failed to create context pool", exc);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void tearDown() {
      cacheLock.lock();

      try {
         if(cache != null) {
            cache.close();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to close LDAP security cache", e);
      }
      finally {
         cacheLock.unlock();
      }
   }

   /**
    * Create a directory context that can be used to query the LDAP server.
    *
    * @return a directory context.
    *
    * @throws NamingException if an error occured during the creation of the
    *                         context.
    */
   protected abstract LdapContext createContext() throws NamingException;

   /**
    * Determines if the connection to a directory context is valid.
    *
    * @param context the context to test.
    *
    * @return <tt>true</tt> if valid; <tt>false</tt> otherwise.
    */
   protected boolean testContext(LdapContext context) {
      return client.testContext(context);
   }

   /**
    * Initialize this authentication provider instance.
    *
    * @param context the directory context.
    *
    * @throws NamingException if an error occured while communicating with the
    *                         LDAP server.
    */
   protected abstract void initInstance(LdapContext context) throws NamingException;

   /**
    * @return the protocol used to communicate with LDAP server.
    */
   protected String getLdapProtocol() {
      return protocol + "://";
   }

   @Override
   public Organization getOrganization(String id) {
      if(Organization.getDefaultOrganizationID().equals(id)) {
         return Organization.getDefaultOrganization();
      }

      return null;
   }

   @Override
   public String getOrgIdFromName(String name) {
      return Organization.getDefaultOrganizationName().equals(name) ?
         Organization.getDefaultOrganizationID() : null;
   }

   @Override
   public String getOrgNameFromID(String id) {
      return Organization.getDefaultOrganizationID().equals(id) ?
         Organization.getDefaultOrganizationName() : null;
   }

   @Override
   public String[] getOrganizationIDs() {
      return new String[] { Organization.getDefaultOrganizationID() };
   }

   @Override
   public String[] getOrganizationNames() {
      return new String[] { Organization.getDefaultOrganizationName() };
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getUsers() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getUsers())
               .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return client.getUsers().keySet().stream()
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list users", e);
         return new IdentityID[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getUsers(IdentityID group) {
      if(group == null) {
         return getIndividualUsers();
      }

      try {
         return searchSubIdentities(group.getName(), Identity.USER).stream()
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to get users in group \"{}\"", group, e);
         return new IdentityID[0];
      }
   }

   /**
    * Search sub identities.
    * @param group group name
    * @param type user or group
    */
   protected final List<String> searchSubIdentities(String group, int type) {
      return Arrays.asList(getCache().getSubIdentities(group, type));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   @Deprecated
   @SuppressWarnings("deprecation")
   public final String[] getEmails(IdentityID user) {
      try {
         return getCache().getEmails(user.getName());
      }
      catch(Exception e) {
         LOG.error("Failed to get email addresses for user: {}", user, e);
         return new String[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getIndividualUsers() {
      try {
         if(isCacheInitialized()) {
            return Arrays.stream(getCache().getIndividualUsers())
               .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return Arrays.stream(client.getIndividualUsers())
            .map(u -> new IdentityID(u, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to get individual uses", e);
         return new IdentityID[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final String[] getIndividualEmailAddresses() {
      try {
         if(isCacheInitialized()) {
            return getCache().getIndividualEmails();
         }

         return client.getIndividualEmailAddresses();
      }
      catch(Exception e) {
         LOG.error("Failed to get individual email addresses", e);
         return new String[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getRoles() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getRoles())
               .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return client.getRoles().keySet().stream()
            .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list the roles", e);
         return new IdentityID[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getRoles(IdentityID user) {
      return Arrays.stream(searchRoles(user.getName(), Identity.USER))
         .map(r -> new IdentityID(r, Organization.getDefaultOrganizationID()))
         .toArray(IdentityID[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final Role getRole(IdentityID roleid) {
      if(roleid != null) {
         if(!existRole(roleid)) {
            return null;
         }

         if(Organization.getDefaultOrganizationID().equals(roleid.getOrgID()) || roleid.getOrgID() == null) {
            return getCache().getRole(roleid.getName());
         }
      }

      return null;
   }

   /**
    * Search parent roles.
    * @param name identity id
    * @param type identity type
    */
   protected final String[] searchRoles(String name, int type) {
      try {
         if(name == null || getRolesSearch(type) == null) {
            return new String[0];
         }

         String dn = getDistinguishedName(name, type);

         if(dn == null) {
            return new String[0];
         }

         return getCache().getRoles(getUserCommonName(name, type), dn, type);
      }
      catch(Exception e) {
         LOG.error(
            "Failed to list the roles assigned to the identity named {} of type {}", name, type, e);
         return new String[0];
      }
   }

   /**
    * Get the common name through user attribute before user-role search.
    * Override by GenericLdapAuthenticationProvider.
    *
    * @return a common name.
    */
   protected String getUserCommonName(String name, int type) {
      return name;
   }

   protected String[] searchRoles(String name, String dn, int type) {
      Set<String> allRoles = Arrays.stream(getRoles())
         .map(IdentityID::getName)
         .collect(Collectors.toSet());
      String filter = MessageFormat.format(getRolesSearch(type), name, dn);
      String attr = getRoleAttribute();
      return Arrays.stream(getRoleBases(getRoleBase()))
         .flatMap(r -> client.searchDirectory(r, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .filter(allRoles::contains)
         .toArray(String[]::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final IdentityID[] getGroups() {
      try {
         if((ignoreCache.get() == null || !ignoreCache.get()) && isCacheInitialized()) {
            return Arrays.stream(getCache().getGroups())
               .map(g -> new IdentityID(g, Organization.getDefaultOrganizationID()))
               .toArray(IdentityID[]::new);
         }

         return client.getGroups().keySet().stream()
            .map(g -> new IdentityID(g, Organization.getDefaultOrganizationID()))
            .toArray(IdentityID[]::new);
      }
      catch(Exception e) {
         LOG.error("Failed to list the groups", e);
         return new IdentityID[0];
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final Group getGroup(IdentityID name) {
      return getCache().getGroup(name.getName());
   }

   /**
    * Gets the distinguished name of the LDAP administrator.
    *
    * @return the DN of the administrator.
    */
   public String getLdapAdministrator() {
      return ldapAdministrator;
   }

   public void setLdapAdministrator(String ldapAdministrator) {
      this.ldapAdministrator = ldapAdministrator;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean authenticate(IdentityID user, Object credential) {
      if(credential == null) {
         LOG.error("Ticket is null, cannot authenticate.");
         return false;
      }

      DefaultTicket ticket;

      if(!(credential instanceof DefaultTicket)) {
         ticket = DefaultTicket.parse(credential.toString());
      }
      else {
         ticket = (DefaultTicket) credential;
      }

      IdentityID userid = ticket.getName();
      String passwd = ticket.getPassword();

      if(userid == null || userid.name.isEmpty() || passwd == null) {
         return false;
      }

      try {
         LdapContext context = borrowContext();

         try {
            return authenticate(userid, passwd, (LdapContext) context.lookup(""));
         }
         finally {
            returnContext(context);
         }
      }
      catch(CommunicationException ce) {
         LOG.error(
            "A communication problem prevented the user from being authenticated: {}", user, ce);
         // throwing custom extension of runtimeException rather than ce so as to not change the
         // signature of authenticate()
         throw new AuthenticatorCommunicationException();
      }
      catch(Exception ne) {
         LOG.error("An error prevented the user from being authenticated: {}", user, ne);
      }

      return false;
   }

   /**
    * Authenticate the specified user.
    */
   protected boolean authenticate(IdentityID userid, String passwd,
      LdapContext context) throws NamingException
   {
      String filter = "(&(" + getUserSearch() +
         ")(" + getUserAttribute() + '=' + Tool.encodeForLDAP(userid.name) +
         "))";
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      String[] userBases = getUserBases(getUserBase());

      for(String userBase : userBases) {
         if(client.authenticate(userBase, filter, passwd, controls, context)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get root.
    */
   public String getRootDn() {
      return rootDn;
   }

   public void setRootDn(String rootDn) {
      this.rootDn = rootDn;
   }

   public String getProtocol() {
      return protocol;
   }

   public void setProtocol(String protocol) {
      this.protocol = protocol;
   }

   public boolean isSearchSubtree() {
      return userScope == SearchControls.SUBTREE_SCOPE;
   }

   public void setSearchSubtree(boolean searchSubtree) {
      if(searchSubtree) {
         userScope = SearchControls.SUBTREE_SCOPE;
      }
      else {
         userScope = SearchControls.ONELEVEL_SCOPE;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void checkParameters() throws SRSecurityException {
      flushContextPool();

      try {
         loadCredential();
         LdapContext context = createContext();

         try {
            if(!testContext(context)) {
               throw
                  new SRSecurityException("Failed to connect to LDAP server");
            }
         }
         finally {
            try {
               context.close();
            }
            catch(Exception exc) {
               LOG.warn("Failed to close directory context", exc);
            }
         }
      }
      catch(CommunicationException exc) {
         Throwable rootEx = exc.getRootCause();

         if(rootEx == null) {
            rootEx = exc;
         }

         String rootMessage = rootEx.toString();

         if(rootMessage.contains("java.net.ConnectException")) {
            throw new SRSecurityException("Invalid Port & Host Name combination", exc);
         }
         else if(rootMessage.contains("java.net.UnknownHostException")) {
            throw new SRSecurityException("Invalid Host Name", exc);
         }
      }
      catch(AuthenticationException exc) {
         throw new SRSecurityException("Invalid Administrator Credentials.", exc);
      }
      catch(NamingException exc) {
         throw new SRSecurityException("Failed to connect to LDAP server", exc);
      }
   }

   @Override
   public void loadCredential(String secretId) {
      JsonNode jsonNode = Tool.loadCredentials(secretId, !isUseCredential());

      if(jsonNode != null) {
         try {
            ldapAdministrator = jsonNode.get("administrator_id").asText();
            password = jsonNode.get("password").asText();
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to load credentials!");
         }
      }
   }

   /**
    * Clear the provider and reiniliaze.
    */
   public void clear() {
      try {
         checkParameters();
      }
      catch(Exception ex) {
         LOG.error("Failed to reload LDAP security provider: " +
            ex.getMessage(), ex);
      }
   }

   protected DataChangeListener changeListener = e -> {
      LOG.debug(e.toString());
      clear();
   };

   /**
    * Get the LDAP directory in which to start searching for users.
    *
    * @return the name of an LDAP entry.
    */
   public String getUserBase() {
      return userBase;
   }

   /**
    * Get the search filter to use when searching for users.
    *
    * @return a search filter.
    */
   public String getUserSearch() {
      return userSearch;
   }

   /**
    * Get the name of the attribute that contains the user ID.
    *
    * @return an attribute name.
    */
   public abstract String getUserAttribute();

   /**
    * Get the name of the attribute that contains the user's email address.
    *
    * @return an attribute name.
    */
   public abstract String getMailAttribute();

   /**
    * Get the LDAP directory in which to start searching for roles.
    *
    * @return the name of an LDAP entry.
    */
   public String getRoleBase() {
      return roleBase;
   }

   /**
    * Get the search filter to use when searching for roles.
    *
    * @return a search filter.
    */
   public abstract String getRoleSearch();

   /**
    * Get the name of the attribute that contains the role name.
    *
    * @return an attribute name.
    */
   public abstract String getRoleAttribute();

   /**
    * Get the search filter to identity when searching for the roles of a specific identity. This
    * should be formatted as specified by MessageFormat, where <code>{0}</code> will be replaced
    * with the identity ID and <code>{1}</code> will be replaced with the distinguished name (DN) of
    * the identity.
    *
    * @param type the identity type.
    *
    * @return a search filter.
    */
   protected abstract String getRolesSearch(int type);

   protected final String getDistinguishedName(String name, int type) {
      return switch(type) {
         case Identity.USER -> getUserDn(name);
         case Identity.GROUP -> getGroupDn(name);
         case Identity.ROLE -> getRoleDn(name);
         default -> null;
      };
   }

   protected final String getUserDn(String name) {
      if(isCacheInitialized()) {
         return getCache().getUserDn(name);
      }

      String filter = "(&(" + getUserSearch() + ")(" + getUserAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      return Arrays.stream(getUserBases(getUserBase()))
         .flatMap(base -> client.searchDirectory(base, filter, userScope, attr).stream())
         .findFirst()
         .orElse(null);
   }

   protected final String getGroupDn(String name) {
      if(isCacheInitialized()) {
         return getCache().getGroupDn(name);
      }

      String filter = "(&(" + getGroupSearch() + ")(" + getGroupAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      return Arrays.stream(getGroupBases(getGroupBase()))
         .flatMap(base -> client.searchDirectory(base, filter, userScope, attr).stream())
         .findFirst()
         .orElse(null);
   }

   protected final String getRoleDn(String name) {
      if(isCacheInitialized()) {
         String dn = getCache().getRoleDn(name);
         return dn;
      }

      String filter = "(&(" + getRoleSearch() + ")(" + getRoleAttribute() + "=" + name + "))";
      String attr = getEntryDNAttribute();
      String dn = Arrays.stream(getRoleBases(getRoleBase()))
         .flatMap(base -> client.searchDirectory(base, filter, SearchControls.SUBTREE_SCOPE, attr).stream())
         .findFirst()
         .orElse(null);
      return dn;
   }

   /**
    * Get the LDAP directory in which to start searching for groups.
    *
    * @return the name of an LDAP entry.
    */
   public String getGroupBase() {
      return groupBase;
   }

   /**
    * Get the search filter to use when searching for groups.
    *
    * @return a search filter.
    */
   public abstract String getGroupSearch();

   /**
    * Get the name of the attribute that contains the group name.
    *
    * @return an attribute name.
    */
   public abstract String getGroupAttribute();

   int getUserScope() {
      return userScope;
   }

   /**
    * Get the user bases.
    */
   protected String[] getUserBases(String users) {
      return parseToUnits(users);
   }

   /**
    * Get the group bases.
    */
   protected String[] getGroupBases(String groups) {
      return parseToUnits(groups);
   }

   /**
    * Get the role bases.
    */
   protected String[] getRoleBases(String roles) {
      return parseToUnits(roles);
   }

   /**
    * Parse the multi bases string into string array
    */
   protected String[] parseToUnits(String multiBases) {
      if(multiBases == null) {
         return new String[] {};
      }
      else if(!multiBases.contains(";")) {
         return new String[] {multiBases};
      }
      else {
         return multiBases.split(";");
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public final User getUser(IdentityID userIdentity) {
      if(userIdentity == null) {
         return null;
      }

      final String lowerCaseName = userIdentity.name.toLowerCase();

      for(IdentityID userName : getUsers()) {
         if(userIdentity.equalsIgnoreCase(userName)) {
            return getCache().getUser(lowerCaseName);
         }
      }

      return null;
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleId) {
      for(String sysAdminRoles : systemAdministratorRoles) {
         if(sysAdminRoles.equals(roleId.getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void clearCache() {
      cacheLock.lock();

      try {
         LdapAuthenticationCache cache = isIgnoreCache() ? this.cache : getCache();

         if(cache != null) {
            cache.load();
         }
      }
      finally {
         cacheLock.unlock();
      }
   }

   @Override
   public boolean isCacheEnabled() {
      return true;
   }

   @Override
   public boolean isLoading() {
      return getCache().isLoading();
   }

   @Override
   public long getCacheAge() {
      return getCache().getAge();
   }

   boolean isCacheInitialized() {
      return getCache().isInitialized();
   }

   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   public void setUserSearch(String userSearch) {
      this.userSearch = userSearch;
   }

   public void setUserBase(String userBase) {
      this.userBase = userBase;
   }

   public void setGroupBase(String groupBase) {
      this.groupBase = groupBase;
   }

   public void setRoleBase(String roleBase) {
      this.roleBase = roleBase;
   }

   public String getUserRolesSearch() {
      return userRolesSearch;
   }

   public void setUserRolesSearch(String userRolesSearch) {
      this.userRolesSearch = userRolesSearch;
   }

   public String[] getSystemAdministratorRoles() {
      return systemAdministratorRoles;
   }

   public void setSystemAdministratorRoles(String[] systemAdministratorRoles) {
      this.systemAdministratorRoles = systemAdministratorRoles;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void readConfiguration(JsonNode configuration) {
      ObjectNode config = (ObjectNode) configuration;
      protocol = config.get("protocol").asText("ldap");
      setSearchSubtree(config.get("searchSubtree").asBoolean(false));
      validateContextAttr = config.get("validateContextAttr").asText("objectclass");
      validateContextBaseDn = config.get("validateContextBaseDn").asText("");
      validateContextSearch = config.get("validateContextSearch").asText("(objectclass=*)");
      rootDn = config.get("rootDn").asText("");
      host = config.get("host").asText("localhost");
      port = config.get("port").asInt(389);
      setUseCredential(config.get("useCredential").asBoolean(false));
      readCredential(config.get("credential").asText());

      ArrayNode sysAdminConfig = (ArrayNode) config.get("sysAdminRoles");
      systemAdministratorRoles = new String[sysAdminConfig.size()];

      for(int i = 0; i < sysAdminConfig.size(); i++) {
         systemAdministratorRoles[i] = sysAdminConfig.get(i).asText();
      }
   }

   private void readCredential(String secretId) {
      if(Tool.isEmptyString(secretId)) {
         return;
      }

      if(isUseCredential()) {
         setSecretId(secretId);
      }
      else {
         loadCredential(secretId);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = mapper.createObjectNode();
      config.put("protocol", protocol);
      config.put("searchSubtree", isSearchSubtree());
      config.put("validateContextAttr", validateContextAttr);
      config.put("validateContextBaseDn", validateContextBaseDn);
      config.put("validateContextSearch", validateContextSearch);
      config.put("rootDn", rootDn);
      config.put("useCredential", isUseCredential());
      config.put("credential", writeCredential());
      config.put("host", host);
      config.put("port", port);
      config.put("userSearch", userSearch);
      config.put("userBase", userBase);
      config.put("groupBase", groupBase);
      config.put("roleBase", roleBase);
      config.put("userRolesSearch", userRolesSearch);

      ArrayNode sysAdminConfig = mapper.createArrayNode();

      for(String sysAdminRole : systemAdministratorRoles) {
         sysAdminConfig.add(sysAdminRole);
      }

      config.set("sysAdminRoles", sysAdminConfig);
      return config;
   }

   private String writeCredential() {
      try {
         if(isUseCredential()) {
            return getSecretId();
         }
         else {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode credential = mapper.createObjectNode()
               .put("administrator_id", ldapAdministrator)
               .put("password", password);

            return Tool.encryptPassword(mapper.writeValueAsString(credential));
         }
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to encrypt credential!");
      }
   }

   /**
    * Get the entrydn name.
    */
   protected String getEntryDNAttribute() {
      return ENTRYDN_ATTRIBUTE;
   }

   /**
    * Get the dn substring of a group.
    */
   protected String getDNString(String dn) {
      if(dn != null) {
         int idx = dn.toLowerCase().indexOf("," + getRootDn().toLowerCase());

         if(idx != -1) {
            return dn.substring(0, idx);
         }
      }

      return null;
   }

   public String getPassword() {
      return password;
   }

   /**
    * Set the password.
    */
   public void setPassword(String password) {
      this.password = password;
   }

   /**
    * Borrows a directory context from the pool.
    *
    * @return a directory context.
    *
    * @throws Exception if a context could not be created.
    */
   protected LdapContext borrowContext() throws Exception {
      return contextPool.borrowObject();
   }

   /**
    * Returns a directory context to the pool.
    *
    * @param context the directory context.
    *
    * @return <tt>true</tt> if the context was borrowed from this provider.
    */
   @SuppressWarnings("UnusedReturnValue")
   protected boolean returnContext(LdapContext context) {
      return contextPool.returnObject(context);
   }

   /**
    * Flushes all pool directory contexts.
    */
   protected void flushContextPool() {
      cacheLock.lock();

      try {
         if(cache != null) {
            cache.reset();
         }
      }
      finally {
         cacheLock.unlock();
      }

      contextPool.flush();
   }

   LdapAuthenticationClient getClient() {
      return client;
   }

   @Override
   public void setProviderName(String providerName) {
      String oldName = getProviderName();
      super.setProviderName(providerName);

      if(!Objects.equals(providerName, oldName)) {
         cacheLock.lock();

         try {
            if(cache != null) {
               try {
                  cache.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close security cache", e);
               }

               cache = null;
            }
         }
         finally {
            cacheLock.unlock();
         }
      }
   }

   @SuppressWarnings("unused")
   public boolean isIgnoreCache() {
      return ignoreCache.get() != null && ignoreCache.get();
   }

   public void setIgnoreCache(boolean ignoreCache) {
      this.ignoreCache.set(ignoreCache);
   }

   LdapAuthenticationCache getCache() {
      return getCache(true);
   }

   LdapAuthenticationCache getCache(boolean initialize) {
      LdapAuthenticationCache result;
      cacheLock.lock();

      try {
         if(cache == null) {
            cache = new LdapAuthenticationCache(this);
         }

         result = cache;
      }
      finally {
         cacheLock.unlock();
      }

      // This needs to be run outside of the lock to prevent a deadlock when the cluster singleton
      // is this instance. Any concurrent calls are handled by the debouncer in the cluster
      // singleton handling of the load call, so possible race conditions here are not a concern.
      if(initialize && !result.isInitialized() && !result.isLoading()) {
         result.load();
      }

      return result;
   }

   protected boolean supportsNamingListener() {
      return true;
   }

   String getValidateContextAttr() {
      return validateContextAttr;
   }

   String getValidateContextBaseDn() {
      return validateContextBaseDn;
   }

   String getValidateContextSearch() {
      return validateContextSearch;
   }

   long getCacheRefreshDelay() {
      return getCacheInterval();
   }

   private final ContextPool contextPool;
   private final LdapAuthenticationClient client;
   private String protocol;
   private int userScope = SearchControls.ONELEVEL_SCOPE;
   private String validateContextAttr = "objectclass";
   private String validateContextBaseDn = "";
   private String validateContextSearch = "(objectclass=*)";
   private String rootDn = "";
   private String password;
   private String host = "localhost";
   private int port = 389;
   private String userSearch = "";
   private String userBase = "";
   private String groupBase = "";
   private String roleBase = "";
   private String userRolesSearch = null;
   private String ldapAdministrator = "cn=manager";
   private String[] systemAdministratorRoles = new String[0];
   private final Lock cacheLock = new ReentrantLock();
   private LdapAuthenticationCache cache;
   private final ThreadLocal<Boolean> ignoreCache = ThreadLocal.withInitial(() -> false);

   private static final String ENTRYDN_ATTRIBUTE = "entrydn";

   private static final Logger LOG =
      LoggerFactory.getLogger(LdapAuthenticationProvider.class);
}
