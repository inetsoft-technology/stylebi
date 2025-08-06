/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

class AuthenticationDAO {
   public AuthenticationDAO(DatabaseAuthenticationProvider provider) {
      this.provider = provider;
   }

   public Optional<UserCredential> getUserCredential(IdentityID username) throws Exception {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserQuery());

            if(provider.isMultiTenant()) {
               query.bind(0, username.orgID);
               query.bind(1, username.name);
            }
            else {
               query.bind(0, username.name);
            }

            return query.map(this::mapToCredential).stream().findFirst();
         }
      }
   }

   public Optional<Map<String, Object>> queryUser(IdentityID userid) throws Exception {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserQuery());

            if(provider.isMultiTenant()) {
               query.bind(0, userid.orgID);
               query.bind(1, userid.name);
            }
            else {
               query.bind(0, userid.name);
            }

            return query.mapToMap().stream().findFirst();
         }
      }
   }

   public QueryResult<IdentityID[]> getUsers() {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserListQuery());
            IdentityID[] result = query.map(this::mapToIdentity).stream()
               .filter(Objects::nonNull)
               .toArray(IdentityID[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the user list, user list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the user list, connection failed.", ex);
      }

      return new QueryResult<>(new IdentityID[0], true);
   }

   public QueryResult<String[]> getOrganizations() {
      if(StringUtils.isBlank(provider.getOrganizationListQuery())) {
         return new QueryResult<>(new String[] { Organization.getDefaultOrganizationID() }, false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getOrganizationListQuery());
            String[] result = query.map(this::mapToOrganizationId).stream()
               .filter(Objects::nonNull)
               .toArray(String[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization list, organization list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization list, connection failed.", ex);
      }

      return new QueryResult<>(new String[0], true);
   }

   public String getOrganizationName(String id) {
      if(StringUtils.isBlank(provider.getOrganizationNameQuery())) {
         return Organization.getDefaultOrganizationName();
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getOrganizationNameQuery());
            query.bind(0, id);
            return query.map(this::mapToOrganizationName).stream()
               .filter(Objects::nonNull)
               .findFirst()
               .orElse(null);
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

   public QueryResult<String[]> getOrganizationRoles(String name) {
      if(StringUtils.isBlank(provider.getOrganizationRolesQuery())) {
         return new QueryResult<>(new String[0], false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getOrganizationRolesQuery());
            query.bind(0, name);
            String[] result = query.map(this::mapToRoleName).stream()
               .filter(Objects::nonNull)
               .toArray(String[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization roles list, organization roles list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization roles list, connection failed.", ex);
      }

      return new QueryResult<>(new String[0], true);
   }

   public QueryResult<String[]> getOrganizationMembers(String id) {
      if(StringUtils.isBlank(provider.getOrganizationMembersQuery())) {
         return new QueryResult<>(new String[0], false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getOrganizationMembersQuery());
            query.bind(0, id);
            String[] result = query.scanResultSet(this::scanOrganizationMembers)
               .toArray(new String[0]);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the organization Members list, organization members list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the organization members list, connection failed.", ex);
      }

      return new QueryResult<>(new String[0], true);
   }

   public QueryResult<IdentityID[]> getUsers(IdentityID group) {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getGroupUsersQuery());

            if(provider.isMultiTenant()) {
               query.bind(0, group.orgID);
               query.bind(1, group.name);
            }
            else {
               query.bind(0, group.name);
            }

            IdentityID[] result = query.map(this::mapToUserName).stream()
               .filter(Objects::nonNull)
               .map(n -> new IdentityID(n, group.orgID))
               .toArray(IdentityID[]::new);
            return new QueryResult<>(result, false);
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

      return new QueryResult<>(new IdentityID[0], true);
   }

   public QueryResult<IdentityID[]> getRoles() {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getRoleListQuery());
            IdentityID[] result = query.map(this::mapToRoleIdentity).stream()
               .filter(Objects::nonNull)
               .distinct()
               .toArray(IdentityID[]::new);
            return new  QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the role list, role list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the role list, connection failed.", ex);
      }

      return new QueryResult<>(new IdentityID[0], true);
   }

   public QueryResult<IdentityID[]> getRoles(IdentityID user) {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserRolesQuery());

            if(provider.isMultiTenant()) {
               query.bind(0, user.orgID);
               query.bind(1, user.name);
            }
            else {
               query.bind(0, user.name);
            }

            IdentityID[] result = query.map((rs, ctx) -> mapToUserRoleIdentity(rs, ctx, user.getOrgID())).stream()
               .filter(Objects::nonNull)
               .toArray(IdentityID[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve user roles, user roles query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve user roles, connection failed.", ex);
      }

      return new QueryResult<>(new IdentityID[0], true);
   }

   public QueryResult<IdentityID[]> getGroups() {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getGroupListQuery());
            IdentityID[] result = query.map(this::mapToGroupIdentity).stream()
               .filter(Objects::nonNull)
               .toArray(IdentityID[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve the group list, group list query is not defined properly.", ex);
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to retrieve the group list, connection failed.", ex);
      }

      return new QueryResult<>(new IdentityID[0], true);
   }

   public QueryResult<String[]> getEmails(IdentityID user) {
      if(StringUtils.isBlank(provider.getUserEmailsQuery())) {
         LOG.debug("Failed to retrieve user emails, user emails query is not defined.");
         return new QueryResult<>(new String[0], false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserEmailsQuery());

            if(provider.isMultiTenant()) {
               query.bind(0, user.orgID);
               query.bind(1, user.name);
            }
            else {
               query.bind(0, user.name);
            }

            String[] result = query.map(this::mapToEmail).stream()
               .filter(Objects::nonNull)
               .toArray(String[]::new);
            return new QueryResult<>(result, false);
         }
         catch(Exception ex) {
            LOG.warn(
               "Failed to retrieve user emails, user emails query is not defined properly.", ex);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to retrieve user emails, connection failed.", e);
      }

      return new QueryResult<>(new String[0], true);
   }

   private UserCredential mapToCredential(ResultSet rs, StatementContext ctx) throws SQLException {
      String password = rs.getString(2);
      String salt = null;

      if(rs.getMetaData().getColumnCount() > 2) {
         salt = rs.getString(3);
      }

      return new  UserCredential(password, salt);
   }

   private IdentityID mapToIdentity(ResultSet rs, StatementContext ctx) throws SQLException {
      String username = rs.getString(1);
      String orgId;

      if(StringUtils.isBlank(username) || "null".equalsIgnoreCase(username)) {
         return null;
      }

      if(provider.isMultiTenant()) {
         orgId = rs.getString(2);

         if(StringUtils.isBlank(orgId)) {
            return null;
         }
      }
      else {
         orgId = Organization.getDefaultOrganizationID();
      }

      return new IdentityID(username, orgId);
   }

   private String mapToOrganizationId(ResultSet rs, StatementContext ctx) throws SQLException {
      String orgId = rs.getString(1);

      if(StringUtils.isBlank(orgId) ||  "null".equalsIgnoreCase(orgId)) {
         return null;
      }

      return orgId;
   }

   private String mapToOrganizationName(ResultSet rs, StatementContext ctx) throws SQLException {
      String name = rs.getString(1);

      if(StringUtils.isBlank(name)) {
         return "";
      }

      return name;
   }

   private String mapToRoleName(ResultSet rs, StatementContext ctx) throws SQLException {
      String name = rs.getString(1);

      if(StringUtils.isBlank(name) || "null".equalsIgnoreCase(name)) {
         return null;
      }

      return name;
   }

   private Set<String> scanOrganizationMembers(Supplier<ResultSet> resultSetSupplier, StatementContext ctx) throws SQLException {
      Set<String> members = new HashSet<>();

      //noinspection unused
      try(StatementContext context = ctx) {
         ResultSet rs = resultSetSupplier.get();

         while(rs.next()) {
            int colCount = rs.getMetaData().getColumnCount();

            for(int i = 1; i <= colCount; i++) {
               String member = rs.getString(i);

               if(!StringUtils.isBlank(member) && !"null".equalsIgnoreCase(member)) {
                  members.add(member);
               }
            }
         }
      }

      return members;
   }

   private String mapToUserName(ResultSet rs, StatementContext ctx) throws SQLException {
      String name = rs.getString(1);

      if(StringUtils.isBlank(name)) {
         return null;
      }

      return name;
   }

   private IdentityID mapToRoleIdentity(ResultSet rs, StatementContext ctx) throws SQLException {
      String role = rs.getString(1);
      String orgID;

      if(StringUtils.isBlank(role) || "null".equalsIgnoreCase(role)) {
         return null;
      }

      if(provider.isAdminRole(role)) {
         orgID = null;
      }
      else if(provider.isMultiTenant()) {
         orgID = rs.getString(2);
      }
      else {
         orgID = Organization.getDefaultOrganizationID();
      }

      if(StringUtils.isBlank(orgID) || "null".equalsIgnoreCase(orgID)) {
         orgID = null;
      }

      return new IdentityID(role, orgID);
   }

   private IdentityID mapToUserRoleIdentity(ResultSet rs, StatementContext ctx, String orgId) throws SQLException {
      String roleName = rs.getString(1);

      if(StringUtils.isBlank(roleName) || "null".equalsIgnoreCase(roleName)) {
         return null;
      }

      if(provider.orgRoleExists(roleName, orgId)) {
         return new IdentityID(roleName, orgId);
      }

      return new IdentityID(roleName, null);
   }

   private IdentityID mapToGroupIdentity(ResultSet rs, StatementContext ctx) throws SQLException {
      String groupName = rs.getString(1);
      String orgID;

      if(StringUtils.isBlank(groupName) || "null".equalsIgnoreCase(groupName)) {
         return null;
      }

      if(provider.isMultiTenant()) {
         orgID = rs.getString(2);

         if(StringUtils.isBlank(orgID) || "null".equalsIgnoreCase(orgID)) {
            return null;
         }
      }
      else {
         orgID = Organization.getDefaultOrganizationID();
      }

      return new IdentityID(groupName, orgID);
   }

   private String mapToEmail(ResultSet rs, StatementContext ctx) throws SQLException {
      String email = rs.getString(1);

      if(StringUtils.isBlank(email)) {
         return null;
      }

      return email;
   }

   private final DatabaseAuthenticationProvider provider;

   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationDAO.class);
}
