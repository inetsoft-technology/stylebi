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
package inetsoft.sree.security.db;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.ClusterCache;
import inetsoft.sree.security.*;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.util.*;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.codec.binary.Hex;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.Serializable;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
      connectionRetryInterval = Long.parseLong(SreeEnv.getProperty(
         "database.security.connection.retryInterval", "600000"));
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

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(userQuery);

            if(SUtil.isMultiTenant()) {
               query.bind(0, username.organization);
               query.bind(1, username.name);
            }
            else {
               query.bind(0, username.name);
            }

            Optional<Object[]> row = query.map(this::mapToArray).stream().findFirst();

            if(row.isPresent()) {
               Object[] result = row.get();
               String dbPassword = (String) result[1];
               String salt = null;

               // check if salt has been included as well
               if(result.length > 2) {
                  salt = (String) result[2];
               }

               return authenticate(password, dbPassword, salt);
            }
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

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(userQuery);

            if(SUtil.isMultiTenant()) {
               query.bind(0, userid.organization);
               query.bind(1, userid.name);
            }
            else {
               query.bind(0, userid.name);
            }
            return query.mapToMap().stream().findFirst().orElse(null);
         }
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
      checkConnectionProperties();
      Driver driver = JDBCHandler.getDriver(driverClass);
      Properties properties = new Properties();
      properties.setProperty("user", dbUser);
      properties.setProperty("password", dbPassword);

      try(Connection connection = driver.connect(url, properties)) {
         if(connection == null) {
            throw new MessageException(Catalog.getCatalog().getString("Connection failed"));
         }
      }
   }

   @Override
   public void tearDown() {
      poolLock.lock();

      try {
         if(connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
         }
      }
      finally {
         poolLock.unlock();
      }

      closeCache();
   }

   @Override
   public IdentityID[] getUsers() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getUsers();
      }
      return Arrays.stream(doGetUsers().result).map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
   }

   private QueryResult doGetUsers() {
      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(userListQuery);
            String[] users;

            if(SUtil.isMultiTenant()) {
               users = query.mapToMap().stream()
                  .filter(map -> {
                     List<Object> values = new ArrayList<>(map.values());
                     return values.get(0) != null && values.get(1) != null &&
                        !values.get(0).toString().isEmpty() && !values.get(1).toString().isEmpty();
                  })
                  .map(map -> {
                     List<Object> values = new ArrayList<>(map.values());
                     return new IdentityID(values.get(0).toString(), values.get(1).toString()).convertToKey();
                  })
                  .toArray(String[]::new);
            }
            else {
               users = query.map(this::mapToString).filter(Objects::nonNull)
                  .map(name -> new IdentityID(name, Organization.getDefaultOrganizationName()).convertToKey()).list().toArray(new String[0]);
            }

            return new QueryResult(users, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the user list, user list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the user list, connection failed.", ex);
      }

      return new QueryResult(new String[0], true);
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
   public Organization getOrganization(String orgKey) {
      if(orgKey == null) {
         return null;
      }

      for(String userName : getOrganizations()) {
         if(caseSensitive && orgKey.equals(userName) ||
            !caseSensitive && orgKey.equalsIgnoreCase(userName))
         {
            return new Organization(orgKey, getOrganizationId(orgKey), getOrganizationMembers(orgKey), "", true);
         }
      }

      return null;
   }

   @Override
   public String getOrgId(String name) {
      return getOrganization(name).getId();
   }

   @Override
   public String getOrgNameFromID(String id) {
      for(String org : getOrganizations()) {
         if(getOrganization(org).getId().equalsIgnoreCase(id)) {
            return org;
         }
      }
      return null;
   }

   @Override
   public String[] getOrganizations() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getOrganizations();
      }

      return doGetOrganizations().result;
   }

   @Override
   public String getOrganizationId(String name) {
      // global
      if(name == null) {
         return null;
      }

      if(cacheEnabled && !isIgnoreCache()) {
         String cacheid = getCache().getOrganizationId(name);

         return cacheid == null || cacheid.isEmpty() ?
                 doGetOrganizationId(name) : cacheid;
      }

      return doGetOrganizationId(name);
   }

   @Override
   public String[] getOrganizationMembers(String name) {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getOrganizationMembers(name);
      }

      return doGetOrganizationMembers(name);
   }

   private QueryResult doGetOrganizations() {
      if(organizationListQuery == null || organizationListQuery.isEmpty()) {
         return new QueryResult(new String[]{Organization.getDefaultOrganizationName()}, false);
      }

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(organizationListQuery);
            String[] orgs = query.map(this::mapToString).filter(Objects::nonNull).list().toArray(new String[0]);
            return new QueryResult(orgs, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization list, organization list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization list, connection failed.", ex);
      }

      return new QueryResult(new String[0], true);
   }

   private String doGetOrganizationId(String name) {
      if(organizationIdQuery == null || organizationIdQuery.isEmpty()) {
         return Organization.getDefaultOrganizationID();
      }

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(organizationIdQuery);
            query.bind(0, name);
            return query.map(this::mapToString).stream().filter(Objects::nonNull).findFirst().orElse(null);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization id, organization id query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization id, connection failed.", ex);
      }

      return "";
   }

   private String[] doGetOrganizationRoles(String name) {
      if(organizationRolesQuery == null || organizationRolesQuery.isEmpty()) {
         return new String[0];
      }

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(organizationRolesQuery);
            query.bind(0, name);
            return query.map(this::mapToString).stream().toArray(String[]::new);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization roles list, organization roles list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization roles list, connection failed.", ex);
      }

      return new String[0];
   }

   private String[] doGetOrganizationMembers(String name) {
      if(organizationMembersQuery == null || organizationMembersQuery.isEmpty()) {
         return new String[0];
      }

      Set<String> members = new HashSet<>();

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(organizationMembersQuery);
            query.bind(0, name);
            query.map(this::mapToList).stream().forEach(members::addAll);

            return members.toArray(new String[0]);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization Members list, organization members list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization members list, connection failed.", ex);
      }

      return new String[0];
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

      return Arrays.stream(doGetUsers(groupIdentity))
         .map(name -> new IdentityID(name, groupIdentity.organization)).toArray(IdentityID[]::new);
   }

   private String[] doGetUsers(IdentityID group) {
      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(groupUsersQuery);

            if(SUtil.isMultiTenant()) {
               query.bind(0, group.organization);
               query.bind(1, group.name);
            }
            else {
               query.bind(0, group.name);
            }
            return query.map(this::mapToString).stream().toArray(String[]::new);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve group users, group users query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn(
            "Failed to retrieve group users, connection failed.", ex);
      }

      return new String[0];
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

      return Arrays.stream(doGetRoles().result).map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
   }

   private QueryResult doGetRoles() {
      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(roleListQuery);
            String[] roles;

            if(SUtil.isMultiTenant()) {
               roles = query.mapToMap().stream()
                  .map(map -> {
                     List<Object> values = new ArrayList<>(map.values());
                     String roleName = values.get(0).toString();
                     String orgName = values.get(1) == null ? null : values.get(1).toString();
                     orgName = fixOrganization(roleName, orgName);
                     return new IdentityID(roleName, orgName).convertToKey();
                  })
                  .toArray(String[]::new);
            }
            else {
               roles = query.map(this::mapToString).filter(Objects::nonNull)
                  .map(name -> {
                     String orgName = fixOrganization(name, Organization.getDefaultOrganizationName());
                     return new IdentityID(name, orgName).convertToKey();
                  }).list().toArray(new String[0]);
            }

            if(systemAdministratorRoles != null && systemAdministratorRoles.length > 0 ||
               orgAdministratorRoles != null && orgAdministratorRoles.length > 0)
            {
               Set<String> set = new java.util.HashSet<>();
               set.addAll(Arrays.asList(roles));
               roles = set.toArray(new String[set.size()]);
            }

            return new QueryResult(roles, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the role list, role list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the role list, connection failed.", ex);
      }

      return new QueryResult(new String[0], true);
   }

   @Override
   public IdentityID[] getRoles(IdentityID userID) {
      if(Arrays.asList(getOrganizations()).contains(userID.organization)) {
         if(cacheEnabled && !isIgnoreCache()) {
            return getCache().getRoles(userID);
         }

         return doGetRoles(userID);
      }
      return new IdentityID[0];
   }

   private IdentityID[] doGetRoles(IdentityID user) {
      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(userRolesQuery);

            if(SUtil.isMultiTenant()) {
               query.bind(0, user.organization);
               query.bind(1, user.name);
               return query.map(this::mapToString).stream().map(r -> new IdentityID(r, parseOrgOrGlobalRole(r, user.organization))).toArray(IdentityID[]::new);
            }
            else {
               query.bind(0, user.name);
               return query.map(this::mapToString).stream().map(r -> new IdentityID(r, user.organization)).toArray(IdentityID[]::new);
            }
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve user roles, user roles query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve user roles, connection failed.", ex);
      }

      return new IdentityID[0];
   }

   private String fixOrganization(String roleName, String orgName) {
      if(systemAdministratorRoles != null) {
         int idx = Arrays.asList(systemAdministratorRoles).indexOf(roleName);

         if(idx != -1) {
            return null;
         }
      }

      if(orgAdministratorRoles != null) {
         int idx = Arrays.asList(orgAdministratorRoles).indexOf(roleName);

         if(idx != -1) {
            return null;
         }
      }

      return orgName;
   }

   @Override
   public Role getRole(IdentityID roleIdentity) {
      if(!existRole(roleIdentity)) {
         return null;
      }

      return new Role(roleIdentity, "");
   }

   private boolean existRole(IdentityID roleIdentity) {
      if(roleIdentity == null) {
         return false;
      }

      IdentityID[] roles = getRoles();

      if(roles == null) {
         return false;
      }

      return Arrays.asList(roles).contains(roleIdentity);
   }

   private String parseOrgOrGlobalRole(String roleName, String userOrg) {
      return getRole(new IdentityID(roleName, userOrg)) == null ? null : userOrg;
   }

   @Override
   public IdentityID[] getGroups() {
      if(cacheEnabled && !isIgnoreCache()) {
         return getCache().getGroups();
      }

      return Arrays.stream(doGetGroups().result).map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
   }

   private QueryResult doGetGroups() {
      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(groupListQuery);
            String[] result;

            if(SUtil.isMultiTenant()) {
               result = query.mapToMap().stream()
                  .filter(map -> {
                     List<Object> values = new ArrayList<>(map.values());
                     return values.get(0) != null && values.get(1) != null &&
                        !values.get(0).toString().isEmpty() && !values.get(1).toString().isEmpty();
                  })
                  .map(map -> {
                     List<Object> values = new ArrayList<>(map.values());
                     return new IdentityID(values.get(0).toString(), values.get(1).toString()).convertToKey();
                  })
                  .toArray(String[]::new);
            }
            else {
               result = query.map(this::mapToString).stream().filter(Objects::nonNull)
                  .map(n -> new IdentityID(n, Organization.getDefaultOrganizationName()).convertToKey()).toArray(String[]::new);

            }
            return new QueryResult(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the group list, group list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the group list, connection failed.", ex);
      }

      return new QueryResult(new String[0], true);
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

      return doGetEmails(userIdentity);
   }

   private String[] doGetEmails(IdentityID user) {
      if(userEmailsQuery == null || userEmailsQuery.isEmpty()) {
         LOG.debug("Failed to retrieve user emails, user emails query is not defined.");
         return new String[0];
      }

      try(Connection connection = getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(userEmailsQuery);

            if(SUtil.isMultiTenant()) {
               query.bind(0, user.organization);
               query.bind(1, user.name);
            }
            else {
               query.bind(0, user.name);
            }
            return query.map(this::mapToString).stream().filter(Objects::nonNull).toArray(String[]::new);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve user emails, user emails query is not defined properly.", ex);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to retrieve user emails, connection failed.", e);
      }

      return new String[0];
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
         (roleId.organization == null || getOrganization(roleId.organization) != null);
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

   public String getOrganizationIdQuery() {
      return organizationIdQuery;
   }

   public void setOrganizationIdQuery(String organizationIdQuery) {
      this.organizationIdQuery = organizationIdQuery;
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
      dbUser = config.get("user").asText("");
      dbPassword = Tool.decryptPassword(config.get("password").asText(""));
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
      organizationIdQuery = config.get("organizationIdQuery").asText("");
      organizationMembersQuery = config.get("organizationMembersQuery").asText("");
      organizationRolesQuery = config.get("organizationRolesQuery").asText("");

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

   @Override
   public JsonNode writeConfiguration(ObjectMapper mapper) {
      ObjectNode config = mapper.createObjectNode();
      config.put("driver", driverClass);
      config.put("url", url);
      config.put("user", dbUser);
      config.put("password", Tool.encryptPassword(dbPassword));
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
      config.put("organizationIdQuery", organizationIdQuery);
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

   @Override
   public void setProviderName(String providerName) {
      String oldName = getProviderName();
      super.setProviderName(providerName);

      if(!Objects.equals(providerName, oldName)) {
         closeCache();
      }
   }

   private Connection getConnection() throws Exception {
      Connection connection = null;
      poolLock.lock();

      try {
         if(connectionValid == null || shouldRetryConnection()) {
            try {
               testConnection();
               connectionValid = true;
            }
            catch(Exception e) {
               connectionValid = false;
               throw e;
            }
            finally {
               connectionLastTested = Instant.now();
            }
         }

         if(connectionValid) {
            if(connectionPool == null) {
               connectionPool = new HikariDataSource();

               Properties properties = new Properties();
               properties.setProperty("user", dbUser);
               properties.setProperty("password", dbPassword);

               try {
                  Driver driver = JDBCHandler.getDriver(driverClass);
                  connectionPool.setDataSource(new DbDataSource(driver, url, properties));
               }
               catch(Exception e) {
                  LOG.warn("Failed to create default data source", e);
                  connectionPool.setDriverClassName(driverClass);
                  connectionPool.setJdbcUrl(url);
                  connectionPool.setUsername(dbUser);
                  connectionPool.setPassword(dbPassword);
               }
            }

            connection = connectionPool.getConnection();
         }
      }
      finally {
         poolLock.unlock();
      }

      if(connection == null) {
         connectionValid = false;
         throw new SQLException("Failed to connect");
      }

      return connection;
   }

   private boolean shouldRetryConnection() {
      return Boolean.FALSE.equals(connectionValid) &&
             connectionLastTested.isBefore(Instant.now().minusMillis(connectionRetryInterval));
   }

   private void resetConnection() {
      poolLock.lock();

      try {
         if(connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
         }

         connectionValid = null;
         connectionLastTested = Instant.MAX;
      }
      finally {
         poolLock.unlock();
      }

      clearCache();
   }

   private void checkConnectionProperties() throws SQLException {
      if(driverClass.isEmpty()) {
         throw new SQLException("Failed to make a connection, the driver class is not defined.");
      }

      if(url.isEmpty()) {
         throw new SQLException("Failed to make a connection, the JDBC URL is not defined.");
      }

      if(dbUser.isEmpty() && requiresLogin) {
         throw new SQLException("Failed to make a connection, user name is not defined.");
      }

      if(!JDBCHandler.isDriverAvailable(Tool.convertUserClassName(driverClass))) {
         throw new SQLException("Failed to make a connection, cannot find the driver class");
      }
   }

   private DbSecurityCache getCache() {
      cacheLock.lock();

      try {
         if(cacheEnabled) {
            if(securityCache == null) {
               securityCache = new DbSecurityCache();
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

   private Object[] mapToArray(ResultSet rs, StatementContext ctx) throws SQLException {
      Object[] result = new Object[rs.getMetaData().getColumnCount()];

      for(int i = 0; i < result.length; i++) {
         result[i] = rs.getObject(i + 1);
      }

      return result;
   }

   private List<String> mapToList(ResultSet rs, StatementContext ctx) throws SQLException {
      List<String> list = new ArrayList<>();

      for(int i = 0; i < rs.getMetaData().getColumnCount(); i++) {
         list.add(rs.getString(i + 1));
      }

      return list;
   }

   private String mapToString(ResultSet r, int columnNumber, StatementContext ctx)
      throws SQLException
   {
      return r.getString(columnNumber);
   }

   private final static int IDENTITY_USER = 0;
   private final static int IDENTITY_GROUP = 1;
   private final static int IDENTITY_ROLE = 2;
   private String userQuery;
   private String userListQuery;
   private String userRolesQuery;
   private String userEmailsQuery;

   private String roleListQuery;

   private String groupListQuery;
   private String groupUsersQuery;

   private String organizationListQuery;
   private String organizationIdQuery;
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
   private final Lock poolLock = new ReentrantLock();
   private Boolean connectionValid;
   private Instant connectionLastTested = Instant.MAX;
   private HikariDataSource connectionPool;
   private final boolean cacheEnabled;
   private final boolean caseSensitive;
   private final long connectionRetryInterval;
   private final Lock cacheLock = new ReentrantLock();
   private DbSecurityCache securityCache;
   private final ThreadLocal<Boolean> ignoreCache = ThreadLocal.withInitial(() -> false);

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuthenticationProvider.class);

   private static final class DbDataSource implements DataSource {
      /**
       * Creates a new instance of <tt>DbDataSource</tt>.
       *
       * @param driver     the JDBC driver.
       * @param url        the JDBC URL for the database.
       * @param properties the connection properties.
       */
      DbDataSource(Driver driver, String url, Properties properties) {
         this.driver = driver;
         this.url = url;
         this.properties = properties;
      }

      @Override
      public Connection getConnection() throws SQLException {
         return driver.connect(url, properties);
      }

      @Override
      public Connection getConnection(String username, String password)
         throws SQLException
      {
         Properties userProperties = new Properties(properties);
         userProperties.setProperty("user", username);
         userProperties.setProperty("password", password);
         return driver.connect(url, userProperties);
      }

      @Override
      public PrintWriter getLogWriter() {
         return logWriter;
      }

      @Override
      public void setLogWriter(PrintWriter out) {
         this.logWriter = out;
      }

      @Override
      public void setLoginTimeout(int seconds) {
         loginTimeout = seconds;
      }

      @Override
      public int getLoginTimeout() {
         return loginTimeout;
      }

      @SuppressWarnings("unchecked")
      @Override
      public <T> T unwrap(Class<T> iface) throws SQLException {
         if(isWrapperFor(iface)) {
            return (T) this;
         }

         throw new SQLException("Does not wrap " + iface);
      }

      @Override
      public boolean isWrapperFor(Class<?> iface) {
         return (iface != null && iface.isAssignableFrom(this.getClass()));
      }

      @Override
      public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
         return driver.getParentLogger();
      }

      private final Driver driver;
      private final String url;
      private final Properties properties;

      private PrintWriter logWriter = null;
      private int loginTimeout = 0;
   }

   private final class DbSecurityCache extends ClusterCache<LoadEvent, LoadData, SaveData> {
      DbSecurityCache() {
         super(false, 500L, TimeUnit.MILLISECONDS, 1000L, TimeUnit.MILLISECONDS,
               getCacheInterval(), TimeUnit.MILLISECONDS,
               "DatabaseSecurityCache:" + getProviderName() + ".",
               USER_ROLES, USER_EMAILS, GROUP_USERS, LISTS, ORGANIZATION_ID, ORGANIZATION_MEMBERS, ORGANIZATION_ROLES);
      }

      @Override
      protected Map<String, Map> doLoad(boolean initializing, LoadData loadData) {
         final QueryResult users = doGetUsers();
         final QueryResult roles = doGetRoles();
         final QueryResult groups = doGetGroups();
         final QueryResult organizations = doGetOrganizations();

         if(users.failed || roles.failed || groups.failed || organizations.failed) {
            reloadCacheAfter = Instant.now().plusSeconds(60);
         }

         Map<String, Map> maps = new HashMap<>();
         Map<String, String> orgIds = new HashMap<>();
         Map<String, String[]> orgMembers = new HashMap<>();
         Map<String, String[]> orgRoles = new HashMap<>();

         for(int i = 0; i < organizations.result.length; i++) {
            String name = organizations.result[i];
            String orgid = doGetOrganizationId(name);
            String[] orgMember = doGetOrganizationMembers(name);
            String[] orgRole = doGetOrganizationRoles(name);
            orgIds.put(name,orgid);
            orgMembers.put(name,orgMember);
            orgRoles.put(name,orgRole);
         }

         maps.put(ORGANIZATION_ID, orgIds);
         maps.put(ORGANIZATION_MEMBERS, orgMembers);
         maps.put(ORGANIZATION_ROLES, orgRoles);
         Map<String, String[]> lists = new HashMap<>();
         lists.put(USERS, users.result);
         lists.put(ROLES, roles.result);
         lists.put(GROUPS, groups.result);
         lists.put(ORGANIZATIONS, Arrays.stream(organizations.result).filter(Objects::nonNull).toArray(String[]::new));
         maps.put(LISTS, lists);

         return maps;
      }

      IdentityID[] getUsers(String[] userNames, Map<String, String[]> userOrgsMap) {
         List<IdentityID> list = new ArrayList<>();

         Arrays.stream(userNames).forEach(u -> {
            String[] orgs = userOrgsMap.get(u);

            for(int i = 0; orgs != null && i < orgs.length; i++) {
               IdentityID id = new IdentityID(u, orgs[i]);

               if(!list.contains(id)) {
                  list.add(id);
               }
            }
         });

         return list.toArray(new IdentityID[list.size()]);
      }

      IdentityID[] getRoles(String[] roleNames, Map<String, String[]> roleOrgsMap) {
         List<IdentityID> list = new ArrayList<>();

         Arrays.stream(roleNames).forEach(r -> {
            String[] orgs = roleOrgsMap.get(r);

            for(int i = 0; orgs != null && i < orgs.length; i++) {
               IdentityID id = new IdentityID(r, orgs[i]);

               if(!list.contains(id)) {
                  list.add(id);
               }
            }
         });

         return list.toArray(new IdentityID[list.size()]);
      }

      IdentityID[] getGroups(String[] groupNames, Map<String, String[]> groupOrgsMap) {
         List<IdentityID> list = new ArrayList<>();

         Arrays.stream(groupNames).forEach(g -> {
            String[] orgs = groupOrgsMap.get(g);

            for(int i = 0; orgs != null && i < orgs.length; i++) {
               IdentityID id = new IdentityID(g, orgs[i]);

               if(!list.contains(id)) {
                  list.add(id);
               }
            }
         });

         return list.toArray(new IdentityID[list.size()]);
      }

      @Override
      protected LoadData getLoadData(LoadEvent event) {
         return new LoadData(System.currentTimeMillis());
      }

      IdentityID[] getUsers() {
         return Arrays.stream(((String[]) get(LISTS, USERS)))
            .map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
      }

      IdentityID[] getUsers(IdentityID group) {
         String[] userNames = getGroupUsers().computeIfAbsent(
            group, DatabaseAuthenticationProvider.this::doGetUsers);
         return Arrays.stream(userNames).map(name -> new IdentityID(name, group.organization)).toArray(IdentityID[]::new);
      }

      IdentityID[] getRoles() {
         return Arrays.stream((String[]) get(LISTS, ROLES))
            .map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
      }

      IdentityID[] getRoles(IdentityID user) {
         Map<IdentityID, ArrayList<IdentityID>> userRoles = getUserRoles();

         if(userRoles.containsKey(user)) {
            return userRoles.get(user) == null ? new IdentityID[0] : userRoles.get(user).stream().toArray(IdentityID[]::new);
         }
         else {
            ArrayList<IdentityID> roles = new ArrayList<IdentityID>(Arrays.asList(doGetRoles(user)));
            userRoles.put(user, roles);
            return roles == null ? new IdentityID[0] : roles.stream().toArray(IdentityID[]::new);
         }
      }

      IdentityID[] getGroups() {
         return Arrays.stream((String[]) get(LISTS, GROUPS))
            .map(IdentityID::getIdentityIDFromKey).toArray(IdentityID[]::new);
      }

      IdentityID[] getOrganizations(String[] orgs) {
         return Arrays.stream(orgs).map(n -> new IdentityID(n,n)).toArray(IdentityID[]::new);
      }

      String[] getOrganizations() {return get(LISTS, ORGANIZATIONS);}

      String getOrganizationId(String name) {
         return getMap(ORGANIZATION_ID).values().isEmpty() ? null :
                 getMap(ORGANIZATION_ID).get(name) == null ? null : getMap(ORGANIZATION_ID).get(name).toString();
      }

      String[] getOrganizationMembers(String orgName) {
         return getMap(ORGANIZATION_MEMBERS).values().isEmpty() ? null :
            getMap(ORGANIZATION_MEMBERS) == null ? null : (String[]) getMap(ORGANIZATION_MEMBERS).get(orgName);
      }

      String[] getEmails(IdentityID user) {
         return getUserEmails().computeIfAbsent(
            user, DatabaseAuthenticationProvider.this::doGetEmails);
      }

      private Map<IdentityID, String[]> getGroupUsers() {
         return getMap(GROUP_USERS);
      }

      private Map<IdentityID, ArrayList<IdentityID>> getUserRoles() {
         return getMap(USER_ROLES);
      }

      private Map<IdentityID, String[]> getUserEmails() {
         return getMap(USER_EMAILS);
      }

      private Instant reloadCacheAfter = Instant.MAX;

      private static final String USER_ROLES = "userRoles";
      private static final String USER_EMAILS = "userEmails";
      private static final String GROUP_USERS = "groupUsers";
      private static final String LISTS = "lists";
      private static final String USERS = "users";
      private static final String GROUPS = "groups";
      private static final String ROLES = "roles";
      private static final String ORGANIZATIONS = "organizations";
      private static final String ORGANIZATION_ID = "organizationId";
      private static final String ORGANIZATION_ROLES = "organizationRoles";
      private static final String ORGANIZATION_MEMBERS = "organizationMembers";
   }

   /*
    These cache data structures are basically empty, but provide a place to pass data or fire events
    if the future if needed.
    */

   public static final class LoadEvent extends EventObject {
      public LoadEvent(Object source) {
         super(source);
      }
   }

   public static final class LoadData implements Serializable {
      public LoadData(long timestamp) {
         this.timestamp = timestamp;
      }

      final long timestamp;
   }

   public static final class SaveData implements Serializable {
      public SaveData(long timestamp) {
         this.timestamp = timestamp;
      }

      final long timestamp;
   }

   /**
    * Wrapper for query result that indicates whether the query failed.
    */
   private static final class QueryResult implements Serializable {
      private QueryResult(String[] result, boolean failed) {
         this.result = result;
         this.failed = failed;
      }

      final String[] result;
      final boolean failed;
   }
}
