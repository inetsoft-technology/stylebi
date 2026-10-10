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
import org.jdbi.v3.core.mapper.MapMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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

            List<UserRow<UserCredential>> rows = query
               .map((rs, ctx) -> {
                  UserCredential credential = mapToCredential(rs, ctx);
                  return new UserRow<>(rs.getString(1), credential, credential);
               })
               .list();
            return selectUserRow(username, rows);
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

            MapMapper mapMapper = new MapMapper();
            List<UserRow<Map<String, Object>>> rows = query
               .map((rs, ctx) -> new UserRow<>(
                  rs.getString(1), mapToOptionalCredential(rs), mapMapper.map(rs, ctx)))
               .list();
            return selectUserRow(userid, rows);
         }
      }
   }

   /**
    * Picks the row of the users query that belongs to the requested user. A single row is
    * returned unchanged. When the query returns several rows (for example, a case-insensitive
    * database collation matching both "bob" and "BOB"), only the rows whose first column equals
    * the bound user name, ignoring trailing spaces, are kept. The kept rows must all carry the
    * same credential, otherwise the lookup is refused because the right row cannot be told apart.
    */
   private <T> Optional<T> selectUserRow(IdentityID user, List<UserRow<T>> rows) {
      if(rows.isEmpty()) {
         return Optional.empty();
      }

      if(rows.size() == 1) {
         return Optional.ofNullable(rows.get(0).value());
      }

      String name = user.name == null ? null : user.name.stripTrailing();
      List<UserRow<T>> matches = rows.stream()
         .filter(row -> row.name() != null && row.name().stripTrailing().equals(name))
         .toList();

      if(matches.isEmpty()) {
         LOG.warn(
            "The users query returned {} rows for user \"{}\" and none of them has a user name " +
            "that exactly matches it, the user will not be authenticated. The users query must " +
            "return a unique row per user name under the database collation.",
            rows.size(), user.name);
         return Optional.empty();
      }

      UserCredential credential = matches.get(0).credential();

      for(UserRow<T> row : matches) {
         if(!Objects.equals(credential, row.credential())) {
            LOG.warn(
               "The users query returned {} rows with different credentials for user \"{}\", " +
               "the user will not be authenticated. The users query must return a unique row " +
               "per user name under the database collation.", matches.size(), user.name);
            return Optional.empty();
         }
      }

      return Optional.ofNullable(matches.get(0).value());
   }

   /**
    * Checks whether the database treats the name of the given user as the name of several users.
    * The users query is bound the same way as the user roles and user emails queries. If its rows
    * carry more than one distinct user name (ignoring trailing spaces), or more than one distinct
    * credential, the name is ambiguous, for example because a case-insensitive collation matches
    * both "bob" and "BOB", or "acme" and "ACME" as organization IDs. The roles and emails queries
    * would then return the rows of all those users, so they must not be used for this user. This
    * is stricter than {@link #selectUserRow}, which can pick the exact-name row: the roles and
    * emails queries cannot pick a row, so any rows that differ in user name or credential are
    * ambiguous. A single row, or rows that only repeat the same user and credential, are not.
    *
    * @return {@code true} if the roles and emails of the user must not be loaded.
    */
   private boolean isAmbiguousUser(Handle handle, IdentityID user) {
      if(user == null || user.name == null || StringUtils.isBlank(provider.getUserQuery())) {
         return false;
      }

      try {
         Query query = handle.createQuery(provider.getUserQuery());

         if(provider.isMultiTenant()) {
            query.bind(0, user.orgID);
            query.bind(1, user.name);
         }
         else {
            query.bind(0, user.name);
         }

         List<UserRow<Void>> rows = query
            .map((rs, ctx) -> new UserRow<Void>(rs.getString(1), mapToOptionalCredential(rs), null))
            .list();

         if(rows.size() < 2) {
            return false;
         }

         long names = rows.stream()
            .map(UserRow::name)
            .filter(Objects::nonNull)
            .map(String::stripTrailing)
            .distinct()
            .count();
         long credentials = rows.stream()
            .map(UserRow::credential)
            .distinct()
            .count();

         if(names > 1 || credentials > 1) {
            LOG.warn(
               "The users query returned rows with different user names or credentials for " +
               "user \"{}\", the roles and emails of this user will not be loaded. Either " +
               "several users have this name under the database collation (user names and " +
               "organization IDs must be unique under it), or the users query does not return " +
               "exactly one row per user: it must not join a column that varies between the " +
               "rows of one user, and in multi-tenant mode it must filter by the organization " +
               "ID parameter.", user.name);
            return true;
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to check that user \"{}\" is unique, the users query failed.",
                  user.name, ex);
      }

      return false;
   }

   private UserCredential mapToOptionalCredential(ResultSet rs) throws SQLException {
      int count = rs.getMetaData().getColumnCount();
      String password = count > 1 ? rs.getString(2) : null;
      String salt = count > 2 ? rs.getString(3) : null;
      return new UserCredential(password, salt);
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
      return getUsers(group, null);
   }

   /**
    * Gets the members of a group.
    *
    * @param group  the group.
    * @param groups the listed groups, indexed by {@link #indexGroups}, used to check that the
    *               database does not merge the group name with another listed group name.
    *               Callers that look up the members of every group pass the list they iterate,
    *               so it is read once per operation and not once per group. If {@code null},
    *               the list is read when it is needed.
    */
   public QueryResult<IdentityID[]> getUsers(IdentityID group, GroupIndex groups) {
      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            List<String> members = queryGroupUsers(handle, group);

            if(isAmbiguousGroup(handle, group, members, groups)) {
               return new QueryResult<>(new IdentityID[0], false);
            }

            IdentityID[] result = members.stream()
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

   /**
    * Indexes the listed groups by the loose key of {@link #isAmbiguousGroup}, for
    * {@link #getUsers(IdentityID, GroupIndex)}.
    */
   GroupIndex indexGroups(Collection<IdentityID> groups) {
      return new GroupIndex(groups, provider.isMultiTenant());
   }

   private List<String> queryGroupUsers(Handle handle, IdentityID group) {
      Query query = handle.createQuery(provider.getGroupUsersQuery());

      if(provider.isMultiTenant()) {
         query.bind(0, group.orgID);
         query.bind(1, group.name);
      }
      else {
         query.bind(0, group.name);
      }

      return query.map(this::mapToUserName).stream()
         .filter(Objects::nonNull)
         .toList();
   }

   /**
    * Checks whether the database treats the name of the given group as the name of another
    * listed group. The group users query only returns member names, so when the database
    * collation matches several groups for the bound name (for example "sales" and "SALES" under
    * a case-insensitive collation, "sales" and "sal&eacute;s" under an accent-insensitive one,
    * "sales" and "sales " under PAD SPACE comparison, full-width and half-width forms or
    * hiragana and katakana under width- or kana-insensitive collations, or organization IDs
    * "acme" and "ACME"), it returns the members of all of them and the rows cannot be
    * attributed to one group.
    * <p>
    * Only the database knows its collation, so a loose comparison of the listed groups (see
    * {@link #looseName}) only finds candidates. The group users query is then run for each
    * candidate: if it returns the same members as for the requested group, the database merged
    * the two names. If the members differ, the database tells the names apart (for example on
    * a case-sensitive database) and the members are used. Groups without a candidate, which is
    * the normal case, run no extra member query.
    *
    * @param members the result of the group users query for the requested group.
    * @param groups  the indexed group list, or {@code null} to read it.
    *
    * @return {@code true} if the members of the group must not be loaded.
    */
   private boolean isAmbiguousGroup(Handle handle, IdentityID group, List<String> members,
                                    GroupIndex groups)
   {
      if(members.isEmpty() || group == null || group.name == null ||
         StringUtils.isBlank(provider.getGroupListQuery()))
      {
         return false;
      }

      if(groups == null || groups.isEmpty()) {
         groups = indexGroups(getGroupList(handle));
      }

      List<String> sortedMembers = null;

      for(IdentityID other : groups.candidates(group)) {
         if(sortedMembers == null) {
            sortedMembers = members.stream().sorted().toList();
         }

         if(sortedMembers.equals(queryGroupUsers(handle, other).stream().sorted().toList())) {
            // every group lookup of every user reaches this, so warn once per group
            String message =
               "The group users query returned the same members for group \"{}\" and group " +
               "\"{}\", the database treats their names as the same name. The members of " +
               "group \"{}\" will not be loaded. Group names (and organization IDs) must be " +
               "unique under the database collation, including case, accents, width, kana " +
               "type and trailing spaces.";

            if(ambiguousGroups.add(group)) {
               LOG.warn(message, group.name, other.name, group.name);
            }
            else {
               LOG.debug(message, group.name, other.name, group.name);
            }

            return true;
         }
      }

      return false;
   }

   /**
    * Gets the group list for {@link #isAmbiguousGroup}. The cached list is used when the cache
    * is enabled, since the cached group members are reset together with it, otherwise the
    * group list query is run on the given handle.
    */
   private List<IdentityID> getGroupList(Handle handle) {
      if(provider.isCacheEnabled() && !provider.isIgnoreCache()) {
         IdentityID[] groups = provider.getGroups();

         if(groups != null && groups.length > 0) {
            return Arrays.asList(groups);
         }
      }

      return handle.createQuery(provider.getGroupListQuery())
         .map(this::mapToGroupIdentity).stream()
         .filter(Objects::nonNull)
         .toList();
   }

   /**
    * Normalizes a name for finding the names that a database collation may treat as the same
    * name. It ignores more than any one collation does, because the database confirms every
    * candidate: compatibility forms (full-width and half-width forms, ligatures), accents and
    * other combining marks, case (with full case folding, e.g. "&szlig;" and "ss"), the
    * difference between hiragana and katakana, and trailing spaces.
    */
   static String looseName(String name) {
      if(name == null) {
         return "";
      }

      String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD);
      normalized = COMBINING_MARKS.matcher(normalized).replaceAll("").stripTrailing()
         .toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
      StringBuilder result = new StringBuilder(normalized.length());

      for(int i = 0; i < normalized.length(); i++) {
         char c = normalized.charAt(i);
         // hiragana U+3041-U+3096 to katakana U+30A1-U+30F6
         result.append(c >= 'ぁ' && c <= 'ゖ' ? (char) (c + 0x60) : c);
      }

      return result.toString();
   }

   /**
    * The listed groups, indexed by the loose key of their name, and of their organization ID
    * in multi-tenant mode, where the group users query binds it.
    */
   static final class GroupIndex {
      GroupIndex(Collection<IdentityID> groups, boolean multiTenant) {
         this.multiTenant = multiTenant;

         for(IdentityID group : groups) {
            if(group != null && group.name != null) {
               index.computeIfAbsent(key(group), k -> new ArrayList<>()).add(group);
            }
         }
      }

      boolean isEmpty() {
         return index.isEmpty();
      }

      /**
       * Gets the listed groups that the database may treat as the given group, other than the
       * group itself. Without multi-tenancy only the name is bound, so the organization ID is
       * ignored.
       */
      List<IdentityID> candidates(IdentityID group) {
         return index.getOrDefault(key(group), List.of()).stream()
            .filter(other -> multiTenant ? !group.equals(other) : !group.name.equals(other.name))
            .toList();
      }

      private String key(IdentityID group) {
         return multiTenant ?
            looseName(group.orgID) + '\u0000' + looseName(group.name) : looseName(group.name);
      }

      private final boolean multiTenant;
      private final Map<String, List<IdentityID>> index = new HashMap<>();
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
            if(isAmbiguousUser(handle, user)) {
               return new QueryResult<>(new IdentityID[0], false);
            }

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

   public QueryResult<Map<IdentityID, IdentityArray>> getUserRoles() {
      if(StringUtils.isBlank(provider.getUserRoleListQuery())) {
         return new QueryResult<>(new HashMap<>(), false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Query query = handle.createQuery(provider.getUserRoleListQuery());
            Map<IdentityID, List<IdentityID>> userRoles = query.reduceRows(new HashMap<>(), (map, row) -> {
               IdentityID user = mapViewToIdentity(row);
               IdentityID role = mapViewToRoleIdentity(row);
               map.computeIfAbsent(user, k -> new ArrayList<>()).add(role);
               return map;
            });
            Map<IdentityID, IdentityArray> result = new HashMap<>();

            for(Map.Entry<IdentityID, List<IdentityID>> entry : userRoles.entrySet()) {
               result.put(entry.getKey(), new IdentityArray(entry.getValue()));
            }

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

      return new QueryResult<>(Map.of(), true);
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
            if(isAmbiguousUser(handle, user)) {
               return new QueryResult<>(new String[0], false);
            }

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

   private IdentityID mapViewToIdentity(RowView row) {
      String username = row.getColumn(1, String.class);
      String orgId;

      if(StringUtils.isBlank(username) || "null".equalsIgnoreCase(username)) {
         return null;
      }

      if(provider.isMultiTenant()) {
         orgId = row.getColumn(3, String.class);

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

   private IdentityID mapViewToRoleIdentity(RowView row) {
      String role = row.getColumn(2, String.class);
      String orgID;

      if(StringUtils.isBlank(role) || "null".equalsIgnoreCase(role)) {
         return null;
      }

      if(provider.isAdminRole(role)) {
         orgID = null;
      }
      else if(provider.isMultiTenant()) {
         orgID = row.getColumn(3, String.class);
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

   /**
    * A row of the users query: the user name from the first column, the credential used to
    * compare duplicate rows, and the mapped value returned to the caller.
    */
   private record UserRow<T>(String name, UserCredential credential, T value) {
   }

   private final DatabaseAuthenticationProvider provider;
   private final Set<IdentityID> ambiguousGroups = ConcurrentHashMap.newKeySet();

   private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationDAO.class);
}
