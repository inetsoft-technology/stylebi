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
package inetsoft.sree.security.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.util.*;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Authentication module that stores user, password, group and role information
 * in a database
 *
 * @author InetSoft Technology
 * @since 12.3
 */
public class DatabaseAuthenticationProvider extends AbstractAuthenticationProvider {
   public DatabaseAuthenticationProvider() {
      cacheEnabled = "true".equals(SreeEnv.getProperty("security.cache"));
      caseSensitive = "true".equals(SreeEnv.getProperty("security.user.caseSensitive", "true"));
   }

   @Override
   public boolean authenticate(IdentityID userIdentity, Object credential) {
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

      IdentityID username = ticket.getName();
      String password = ticket.getPassword();

      if(username == null || username.name.isEmpty() || password == null) {
         return false;
      }

      if(userQuery.isEmpty()) {
         LOG.warn("Failed to authenticate, users query is not defined.");
         return false;
      }

      try {
         Optional<UserCredential> passwordAndSalt = dao.getUserCredential(username);

         if(passwordAndSalt.isPresent()) {
            return authenticate(password, passwordAndSalt.get().password(), passwordAndSalt.get().salt());
         }
      }
      catch(Exception e) {
         LOG.error("An exception prevented user \"{}\" from being authenticated", username, e);
      }

      return false;
   }

   @NotNull
   public Map<String, Object> queryUser(IdentityID userid) {
      if(userid == null || userid.name.isEmpty()) {
         return Collections.emptyMap();
      }

      if(userQuery.isEmpty()) {
         LOG.warn("users query is not defined.");
         return Collections.emptyMap();
      }

      try {
         return dao.queryUser(userid).orElse(null);
      }
      catch(Exception e) {
         LOG.error("An exception prevented user query \"{}\"", userid.convertToKey(), e);
      }

      return Collections.emptyMap();
   }

   /**
    * Check if the entered password matches the entry in the database
    *
    * @param password   the entered password
    * @param dbPassword the password hash from the database
    * @param salt       the salt for the password
    */
   private boolean authenticate(String password, String dbPassword, String salt) {
      if(hashAlgorithm == null || hashAlgorithm.isEmpty() || hashAlgorithm.equals("None")) {
         return Objects.equals(password, dbPassword);
      }

      return Tool.checkHashedPassword(
         dbPassword, password, hashAlgorithm, salt, appendSalt, Hex::encodeHexString);
   }

   public void testConnection() throws Exception {
      connectionProvider.testConnection();
   }

   @Override
   public void loadCredential(String secretId) {
      JsonNode jsonNode = Tool.loadCredentials(secretId, !isUseCredential());

      if(jsonNode != null) {
         try {
            dbUser = jsonNode.get("user").asText();
            dbPassword = jsonNode.get("password").asText();
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to load credentials!");
         }
      }
   }

   @Override
   public void tearDown() {
      connectionProvider.close();
      closeCache();
   }

   @Override
   public IdentityID[] getUsers() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getUsers();
      }

      return dao.getUsers().result();
   }

   @Override
   public User getUser(IdentityID userIdentity) {
      if(userIdentity == null) {
         return null;
      }

      for(IdentityID userName : getUsers()) {
         if(caseSensitive && userIdentity.equals(userName) ||
            !caseSensitive && userIdentity.equalsIgnoreCase(userName))
         {
            return new User(userIdentity, getEmails(userIdentity),
                            getUserGroups(userIdentity, caseSensitive), getRoles(userIdentity), "", "");
         }
      }

      return null;
   }

   @Override
   public Organization getOrganization(String id) {
      if(id == null) {
         return null;
      }

      for(String orgID : this.getOrganizationIDs()) {
         if(caseSensitive && id.equals(orgID) ||
            !caseSensitive && id.equalsIgnoreCase(orgID))
         {
            return new Organization(getOrganizationName(id), id, getOrganizationMembers(id), "", true);
         }
      }

      return null;
   }

   @Override
   public String getOrgIdFromName(String name) {
      for(String orgID : getOrganizationIDs()) {
         if(getOrganization(orgID).getName().equals(name)) {
            return orgID;
         }
      }
      return null;
   }

   @Override
   public String getOrgNameFromID(String id) {
      return getOrganization(id).getName();
   }

   @Override
   public String[] getOrganizationIDs() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getOrganizations();
      }

      return dao.getOrganizations().result();
   }

   @Override
   public String[] getOrganizationNames() {
      if(cacheEnabled && !isIgnoreCache()) {
         return Arrays.stream(getCache().getOrganizations())
            .map(this::getOrganizationName)
            .toArray(String[]::new);
      }

      return Arrays.stream(dao.getOrganizations().result())
         .map(dao::getOrganizationName)
         .toArray(String[]::new);
   }

   public String getOrganizationName(String id) {
      // global
      if(id == null) {
         return null;
      }

      if(cacheEnabled && !isIgnoreCache()) {
         String cacheid = getCache().getOrganizationName(id);

         return cacheid == null || cacheid.isEmpty() ?
                 dao.getOrganizationName(id) : cacheid;
      }

      return dao.getOrganizationName(id);
   }

   @Override
   public String getOrganizationId(String name) {
      // global
      for(String oid : getOrganizationIDs()) {
         if(getOrganization(oid).getName().equals(name)) {
            return oid;
         }
      }
      return null;
   }

   @Override
   public String[] getOrganizationMembers(String organizationID) {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getOrganizationMembers(organizationID);
      }

      return dao.getOrganizationMembers(organizationID).result();
   }

   @Override
   public Group getGroup(IdentityID groupIdentity) {
      for(IdentityID groupId : getGroups()) {
         if(Tool.equals(groupId, groupIdentity)) {
            return new Group(groupIdentity, null, new String[0], new IdentityID[0]);
         }
      }
      return null;
   }

   @Override
   public IdentityID[] getUsers(IdentityID groupIdentity) {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getUsers(groupIdentity);
      }

      return dao.getUsers(groupIdentity).result();
   }

   @Override
   public IdentityID[] getIndividualUsers() {
      List<IdentityID> individualUserList = new ArrayList<>();

      for(IdentityID user : getUsers()) {
         if(getUserGroups(user).length == 0) {
            individualUserList.add(user);
         }
      }

      return individualUserList.toArray(new IdentityID[0]);
   }

   @Override
   public IdentityID[] getRoles() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getRoles();
      }

      return dao.getRoles().result();
   }

   @Override
   public IdentityID[] getRoles(IdentityID userID) {
      if(Arrays.asList(this.getOrganizationIDs()).contains(userID.orgID)) {
         if(cacheEnabled && !isIgnoreCache()) {
            return getCache().getRoles(userID);
         }

         return dao.getRoles(userID).result();
      }

      return new IdentityID[0];
   }

   @Override
   public Role getRole(IdentityID roleIdentity) {
      if(!existRole(roleIdentity)) {
         return null;
      }

      return new Role(roleIdentity, "");
   }

   @Override
   public IdentityID[] getGroups() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getGroups();
      }

      return dao.getGroups().result();
   }

   /**
    * @deprecated
    */
   @SuppressWarnings("deprecation")
   @Override
   @Deprecated
   public String[] getEmails(IdentityID userIdentity) {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getEmails(userIdentity);
      }

      return dao.getEmails(userIdentity).result();
   }

   @Override
   public boolean isSystemAdministratorRole(IdentityID roleId) {
      return isSystemAdministratorRole(roleId.name);
   }

   private boolean isSystemAdministratorRole(String roleName) {
      for(String sysAdminRoles : systemAdministratorRoles) {
         if(sysAdminRoles.equals(roleName)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isOrgAdministratorRole(IdentityID roleId) {
      return isOrgAdministratorRole(roleId.name) &&
         (roleId.orgID == null || getOrganization(roleId.orgID) != null);
   }

   private boolean isOrgAdministratorRole(String roleName) {
      for(String orgAdminRoles : orgAdministratorRoles) {
         String orgAdminName = IdentityID.getIdentityIDFromKey(orgAdminRoles).name;

         if(orgAdminName.equals(roleName)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isCacheEnabled() {
      return cacheEnabled;
   }

   @Override
   public void clearCache() {
      if(cacheEnabled) {
         cacheLock.lock();

         try {
            if(securityCache != null) {
               securityCache.load();
            }
         }
         finally {
            cacheLock.unlock();
         }
      }
   }

   @Override
   public boolean isLoading() {
      return cacheEnabled && getCache().isLoading();
   }

   @Override
   public long getCacheAge() {
      return cacheEnabled ? getCache().getAge() : 0L;
   }

   public String getUserQuery() {
      return userQuery;
   }

   public void setUserQuery(String userQuery) {
      this.userQuery = userQuery;
      clearCache();
   }

   public String getGroupListQuery() {
      return groupListQuery;
   }

   public void setGroupListQuery(String groupListQuery) {
      this.groupListQuery = groupListQuery;
      clearCache();
   }

   public String getOrganizationListQuery() {
      return organizationListQuery;
   }

   public void setOrganizationListQuery(String organizationListQuery) {
      this.organizationListQuery = organizationListQuery;
      clearCache();
   }

   public String getUserListQuery() {
      return userListQuery;
   }

   public void setUserListQuery(String userListQuery) {
      this.userListQuery = userListQuery;
      clearCache();
   }

   public String getGroupUsersQuery() {
      return groupUsersQuery;
   }

   public void setGroupUsersQuery(String groupUsersQuery) {
      this.groupUsersQuery = groupUsersQuery;
      clearCache();
   }

   public String getOrganizationNameQuery() {
      return organizationNameQuery;
   }

   public void setOrganizationNameQuery(String organizationNameQuery) {
      this.organizationNameQuery = organizationNameQuery;
      clearCache();
   }

   public String getOrganizationMembersQuery() {
      return organizationMembersQuery;
   }

   public void setOrganizationMembersQuery(String organizationMembersQuery) {
      this.organizationMembersQuery = organizationMembersQuery;
      clearCache();
   }

   public String getOrganizationRolesQuery() {
      return organizationRolesQuery;
   }

   public void setOrganizationRolesQuery(String organizationRolesQuery) {
      this.organizationRolesQuery = organizationRolesQuery;
      clearCache();
   }

   public String getRoleListQuery() {
      return roleListQuery;
   }

   public void setRoleListQuery(String roleListQuery) {
      this.roleListQuery = roleListQuery;
      clearCache();
   }

   public String getUserRolesQuery() {
      return userRolesQuery;
   }

   public void setUserRolesQuery(String userRolesQuery) {
      this.userRolesQuery = userRolesQuery;
      clearCache();
   }

   public String getUserEmailsQuery() {
      return userEmailsQuery;
   }

   public void setUserEmailsQuery(String userEmailsQuery) {
      this.userEmailsQuery = userEmailsQuery;
      clearCache();
   }

   public String getHashAlgorithm() {
      if(hashAlgorithm == null || hashAlgorithm.isEmpty()) {
         hashAlgorithm = "None";
      }

      return hashAlgorithm;
   }

   public void setHashAlgorithm(String hashAlgorithm) {
      if(hashAlgorithm == null || hashAlgorithm.isEmpty()) {
         this.hashAlgorithm = "None";
      }
      else {
         this.hashAlgorithm = hashAlgorithm;
      }

      clearCache();
   }

   public boolean isAppendSalt() {
      return appendSalt;
   }

   public void setAppendSalt(boolean appendSalt) {
      this.appendSalt = appendSalt;
      clearCache();
   }

   public String getDriver() {
      return driverClass;
   }

   public void setDriver(String driver) {
      this.driverClass = driver;
      resetConnection();
   }

   public String getUrl() {
      return url;
   }

   public void setUrl(String url) {
      this.url = url;
      resetConnection();
   }

   public boolean isRequiresLogin() {
      return requiresLogin;
   }

   public void setRequiresLogin(boolean requiresLogin) {
      this.requiresLogin = requiresLogin;
   }

   public String getDbUser() {
      return dbUser;
   }

   public void setDbUser(String dbUser) {
      this.dbUser = dbUser;
      resetConnection();
   }

   public String getDbPassword() {
      return dbPassword;
   }

   public void setDbPassword(String dbPassword) {
      this.dbPassword = dbPassword;
      resetConnection();
   }

   public String[] getSystemAdministratorRoles() {
      return systemAdministratorRoles;
   }

   public void setSystemAdministratorRoles(String[] systemAdministratorRoles) {
      this.systemAdministratorRoles = systemAdministratorRoles;
      clearCache();
   }

   public String[] getOrgAdministratorRoles() {
      return orgAdministratorRoles;
   }

   public void setOrgAdministratorRoles(String[] orgAdministratorRoles) {
      this.orgAdministratorRoles = orgAdministratorRoles;
      clearCache();
   }

   @Override
   public void readConfiguration(JsonNode configuration) {
      ObjectNode config = (ObjectNode) configuration;
      driverClass = config.get("driver").asText("");
      url = config.get("url").asText("");
      hashAlgorithm = config.get("hashAlgorithm").asText("None");
      appendSalt = config.get("appendSalt").asBoolean(true);
      requiresLogin = config.get("requiresLogin").asBoolean(true);
      userQuery = config.get("userQuery").asText("");
      groupListQuery = config.get("groupListQuery").asText("");
      organizationListQuery = config.get("organizationListQuery").asText("");
      userListQuery = config.get("userListQuery").asText("");
      groupUsersQuery = config.get("groupUsersQuery").asText("");
      roleListQuery = config.get("roleListQuery").asText("");
      userRolesQuery = config.get("userRolesQuery").asText("");
      userEmailsQuery = config.get("userEmailsQuery").asText("");
      organizationNameQuery = config.get("organizationNameQuery").asText("");
      organizationMembersQuery = config.get("organizationMembersQuery").asText("");
      organizationRolesQuery = config.get("organizationRolesQuery").asText("");
      setUseCredential(config.get("useCredential").asBoolean(false));
      readCredential(config.get("credential").asText());

      ArrayNode sysAdminConfig = (ArrayNode) config.get("sysAdminRoles");
      systemAdministratorRoles = new String[sysAdminConfig.size()];

      for(int i = 0; i < sysAdminConfig.size(); i++) {
         systemAdministratorRoles[i] = sysAdminConfig.get(i).asText();
      }

      ArrayNode orgAdminConfig = (ArrayNode) config.get("orgAdminRoles");

      if(orgAdminConfig != null) {
         orgAdministratorRoles = new String[orgAdminConfig.size()];

         for(int i = 0; i < orgAdminConfig.size(); i++) {
            orgAdministratorRoles[i] = orgAdminConfig.get(i).asText();
         }
      }

      if(hashAlgorithm.isEmpty()) {
         hashAlgorithm = "None";
      }

      resetConnection();
   }

   private void readCredential(String secretId) {
      if(!requiresLogin || Tool.isEmptyString(secretId)) {
         return;
      }

      if(isUseCredential()) {
         setSecretId(secretId);
      }
      else {
         loadCredential(secretId);
      }
   }

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = mapper.createObjectNode();
      config.put("driver", driverClass);
      config.put("url", url);
      config.put("useCredential", isUseCredential());
      config.put("credential", writeCredential());
      config.put("hashAlgorithm", getHashAlgorithm());
      config.put("appendSalt", appendSalt);
      config.put("requiresLogin", requiresLogin);
      config.put("userQuery", userQuery);
      config.put("groupListQuery", groupListQuery);
      config.put("userListQuery", userListQuery);
      config.put("groupUsersQuery", groupUsersQuery);
      config.put("organizationListQuery", organizationListQuery);
      config.put("roleListQuery", roleListQuery);
      config.put("userRolesQuery", userRolesQuery);
      config.put("userEmailsQuery", userEmailsQuery);
      config.put("organizationNameQuery", organizationNameQuery);
      config.put("organizationMembersQuery", organizationMembersQuery);
      config.put("organizationRolesQuery", organizationRolesQuery);

      ArrayNode sysAdminConfig = mapper.createArrayNode();

      for(String sysAdminRole : systemAdministratorRoles) {
         sysAdminConfig.add(sysAdminRole);
      }

      config.set("sysAdminRoles", sysAdminConfig);

      if(orgAdministratorRoles != null && orgAdministratorRoles.length > 0) {
         ArrayNode orgAdminConfig = mapper.createArrayNode();

         for(String orgAdminRole : orgAdministratorRoles) {
            orgAdminConfig.add(orgAdminRole);
         }

         config.set("orgAdminRoles", orgAdminConfig);
      }

      return config;
   }

   private String writeCredential() {
      if(requiresLogin) {
         try {
            if(isUseCredential()) {
               return getSecretId();
            }
            else {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode credential = mapper.createObjectNode()
                  .put("user", dbUser)
                  .put("password", dbPassword);

               return Tool.encryptPassword(mapper.writeValueAsString(credential));
            }
         }
         catch(Exception e) {
            throw new RuntimeException("Failed to encrypt credential!");
         }
      }

      return null;
   }

   @Override
   public void setProviderName(String providerName) {
      String oldName = getProviderName();
      super.setProviderName(providerName);

      if(!Objects.equals(providerName, oldName)) {
         closeCache();
      }
   }

   public void resetConnection() {
      connectionProvider.resetConnection();
      clearCache();
   }

   private DatabaseAuthenticationCache getCache() {
      return getCache(true);
   }

   DatabaseAuthenticationCache getCache(boolean initialize) {
      cacheLock.lock();

      try {
         if(cacheEnabled) {
            if(securityCache == null) {
               securityCache = new DatabaseAuthenticationCache(this);
            }

            securityCache.initialize();

            if(securityCache.reloadCacheAfter.isBefore(Instant.now())) {
               securityCache.reloadCacheAfter = Instant.MAX;
               resetConnection();
            }
         }
      }
      finally {
         cacheLock.unlock();
      }

      return securityCache;
   }

   private void closeCache() {
      cacheLock.lock();

      try {
         if(securityCache != null) {
            try {
               securityCache.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close security cache", e);
            }

            securityCache = null;
         }
      }
      finally {
         cacheLock.unlock();
      }
   }

   public boolean isIgnoreCache() {
      return ignoreCache.get() != null && ignoreCache.get();
   }

   public void setIgnoreCache(boolean ignoreCache) {
      this.ignoreCache.set(ignoreCache);
   }

   private ConnectionProperties getConnectionProperties() {
      loadCredential();
      return new ConnectionProperties(driverClass, url, dbUser, dbPassword, requiresLogin);
   }

   ConnectionProvider getConnectionProvider() {
      return connectionProvider;
   }

   AuthenticationDAO getDao() {
      return dao;
   }

   boolean isAdminRole(String roleName) {
      if(systemAdministratorRoles != null) {
         int idx = Arrays.asList(systemAdministratorRoles).indexOf(roleName);

         if(idx != -1) {
            return true;
         }
      }

      if(orgAdministratorRoles != null) {
         int idx = Arrays.asList(orgAdministratorRoles).indexOf(roleName);

         if(idx != -1) {
            return true;
         }
      }

      return false;
   }

   boolean orgRoleExists(String roleName, String userOrg) {
      return getRole(new IdentityID(roleName, userOrg)) != null;
   }

   long getCacheRefreshDelay() {
      return getCacheInterval();
   }

   private String userQuery;
   private String userListQuery;
   private String userRolesQuery;
   private String userEmailsQuery;

   private String roleListQuery;

   private String groupListQuery;
   private String groupUsersQuery;

   private String organizationListQuery;
   private String organizationNameQuery;
   private String organizationRolesQuery;
   private String organizationMembersQuery;

   private String hashAlgorithm;
   private boolean appendSalt;
   private boolean requiresLogin;
   private String driverClass;
   private String url;
   private String dbUser;
   private String dbPassword;
   private String[] systemAdministratorRoles = new String[0];
   private String[] orgAdministratorRoles = new String[0];
   private final boolean cacheEnabled;
   private final boolean caseSensitive;
   private final ConnectionProvider connectionProvider =
      new ConnectionProvider(this::getConnectionProperties);
   private final AuthenticationDAO dao = new AuthenticationDAO(this);
   private final Lock cacheLock = new ReentrantLock();
   private DatabaseAuthenticationCache securityCache;
   private final ThreadLocal<Boolean> ignoreCache = ThreadLocal.withInitial(() -> false);

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuthenticationProvider.class);
}
