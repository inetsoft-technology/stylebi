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
import java.text.Collator;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
    * @return {@link Ambiguity#AMBIGUOUS} if the roles and emails of the user must not be
    *         loaded, or {@link Ambiguity#FAILED} if the users query failed, in which case they
    *         must not be loaded either, but the result must not be cached.
    */
   private Ambiguity isAmbiguousUser(Handle handle, IdentityID user) {
      if(user == null || user.name == null || StringUtils.isBlank(provider.getUserQuery())) {
         return Ambiguity.NONE;
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
            return Ambiguity.NONE;
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
            return Ambiguity.AMBIGUOUS;
         }
      }
      catch(Exception ex) {
         // fail closed: without the check, the roles and emails of other users may be loaded
         LOG.warn(
            "Failed to check that user \"{}\" is unique, the users query failed. The roles " +
            "and emails of this user will not be loaded.", user.name, ex);
         return Ambiguity.FAILED;
      }

      return Ambiguity.NONE;
   }

   /**
    * The result of {@link #isAmbiguousUser}.
    */
   private enum Ambiguity {
      NONE, AMBIGUOUS, FAILED
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
      return getOrganizationMembers(id, null);
   }

   /**
    * Gets the members of an organization.
    *
    * @param id     the organization ID.
    * @param orgIDs the listed organization IDs, used to check that the database does not merge
    *               the organization ID with another listed one. If {@code null}, the
    *               organization list query is run when it is needed.
    */
   public QueryResult<String[]> getOrganizationMembers(String id, Collection<String> orgIDs) {
      if(StringUtils.isBlank(provider.getOrganizationMembersQuery())) {
         return new QueryResult<>(new String[0], false);
      }

      try(Connection connection = provider.getConnectionProvider().getConnection()) {
         Jdbi jdbi = Jdbi.create(connection);

         try(Handle handle = jdbi.open()) {
            Set<String> members = queryOrganizationMembers(handle, id);

            if(isMergedOrganization(handle, id, members, orgIDs)) {
               return new QueryResult<>(new String[0], false);
            }

            return new QueryResult<>(members.toArray(new String[0]), false);
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

   private Set<String> queryOrganizationMembers(Handle handle, String id) {
      Query query = handle.createQuery(provider.getOrganizationMembersQuery());
      query.bind(0, id);
      return query.scanResultSet(this::scanOrganizationMembers);
   }

   /**
    * Checks whether the database treats the given organization ID as another listed
    * organization ID in the organization members query (for example "acme" and "ACME" under a
    * case-insensitive collation), in the same way as {@link #isAmbiguousGroup}: listed IDs with
    * the same {@link #candidateKey} are candidates, and the members query is run for each of
    * them. The same non-empty members mean that the database merged the two IDs.
    *
    * @return {@code true} if the members of the organization must not be loaded.
    */
   private boolean isMergedOrganization(Handle handle, String id, Set<String> members,
                                        Collection<String> orgIDs)
   {
      if(members.isEmpty() || id == null ||
         StringUtils.isBlank(provider.getOrganizationListQuery()))
      {
         return false;
      }

      if(orgIDs == null) {
         orgIDs = handle.createQuery(provider.getOrganizationListQuery())
            .map(this::mapToOrganizationId).stream()
            .filter(Objects::nonNull)
            .toList();
      }

      Collator collator = createCandidateCollator();
      String key = candidateKey(collator, id);

      for(String other : orgIDs) {
         if(other != null && !other.equals(id) && key.equals(candidateKey(collator, other)) &&
            members.equals(queryOrganizationMembers(handle, other)))
         {
            LOG.warn(
               "The organization members query returned the same members for organization " +
               "\"{}\" and organization \"{}\", the database treats these IDs as the same ID. " +
               "The members of organization \"{}\" will not be loaded. Organization IDs must " +
               "be unique under the database collation.", id, other, id);
            return true;
         }
      }

      return false;
   }

   public QueryResult<IdentityID[]> getUsers(IdentityID group) {
      return getUsers(group, null);
   }

   /**
    * Gets the members of a group.
    *
    * @param group  the group.
    * @param groups the listed groups, indexed by {@link #indexIdentities}, used to check that the
    *               database does not merge the group name with another listed group name.
    *               Callers that look up the members of every group pass the list they iterate,
    *               so it is read once per operation and not once per group. If {@code null},
    *               the list is read when it is needed.
    */
   public QueryResult<IdentityID[]> getUsers(IdentityID group, IdentityIndex groups) {
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
    * Indexes listed groups or users by the loose key of {@link #isAmbiguousGroup} and
    * {@link #isMergedUser}, for {@link #getUsers(IdentityID, IdentityIndex)} and
    * {@link #getUserRoles(Collection)}.
    */
   IdentityIndex indexIdentities(Collection<IdentityID> identities) {
      return new IdentityIndex(identities, provider.isMultiTenant());
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
                                    IdentityIndex groups)
   {
      if(members.isEmpty() || group == null || group.name == null ||
         StringUtils.isBlank(provider.getGroupListQuery()))
      {
         return false;
      }

      if(groups == null || groups.isEmpty()) {
         groups = indexIdentities(getGroupList(handle));
      }

      List<String> sortedMembers = null;

      for(IdentityID other : groups.candidates(group)) {
         if(sortedMembers == null) {
            sortedMembers = members.stream().sorted().toList();
         }

         if(sortedMembers.equals(queryGroupUsers(handle, other).stream().sorted().toList())) {
            // every group lookup of every user reaches this, so warn once per group
            String message =
               "The group users query returned the same members for group {} and group " +
               "{}, the database treats their names as the same name. The members of " +
               "group {} will not be loaded. Group names (and organization IDs) must be " +
               "unique under the database collation, including case, accents, width, kana " +
               "type and trailing spaces.";

            // in multi-tenant mode the names can be equal and only the organization IDs differ
            String groupLabel = identityLabel(group);
            String otherLabel = identityLabel(other);

            if(ambiguousGroups.add(group)) {
               LOG.warn(message, groupLabel, otherLabel, groupLabel);
            }
            else {
               LOG.debug(message, groupLabel, otherLabel, groupLabel);
            }

            return true;
         }
      }

      return false;
   }

   /**
    * Gets the group or user name for a log message, with the organization ID in multi-tenant
    * mode.
    */
   private String identityLabel(IdentityID identity) {
      return provider.isMultiTenant() ?
         "\"" + identity.name + "\" (organization \"" + identity.orgID + "\")" :
         "\"" + identity.name + "\"";
   }

   /**
    * Gets the group list for {@link #isAmbiguousGroup}. The cached list is used when the cache
    * is enabled, since the cached group members are reset together with it, otherwise the
    * group list query is run on the given handle. In multi-tenant mode the query is always
    * run, see {@link #getUserList}.
    */
   private List<IdentityID> getGroupList(Handle handle) {
      if(provider.isCacheEnabled() && !provider.isIgnoreCache() && !provider.isMultiTenant()) {
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
    * Creates the collator for {@link #candidateKey}: the root locale at primary strength with
    * full decomposition.
    */
   static Collator createCandidateCollator() {
      Collator collator = Collator.getInstance(Locale.ROOT);
      collator.setStrength(Collator.PRIMARY);
      collator.setDecomposition(Collator.FULL_DECOMPOSITION);
      return collator;
   }

   /**
    * Gets the key that groups the names a database collation may treat as the same name: the
    * collation key of {@link #looseName} under the given {@link #createCandidateCollator
    * collator}. The collation key also merges expansions ("&aelig;" and "ae", "&oelig;" and
    * "oe") and ignorable characters (e.g. a zero-width space), as accent- and case-insensitive
    * collations do, and {@link #looseName} adds the width, kana and trailing space folding that
    * the collator lacks. A {@link Collator} is not thread-safe, so the caller must not share it
    * between threads.
    */
   static String candidateKey(Collator collator, String name) {
      return HexFormat.of().formatHex(collator.getCollationKey(looseName(name)).toByteArray());
   }

   /**
    * The listed groups or users, indexed by the candidate key of their name, and of their
    * organization ID in multi-tenant mode, where the group users, user roles and user emails
    * queries bind it.
    */
   static final class IdentityIndex {
      IdentityIndex(Collection<IdentityID> identities, boolean multiTenant) {
         this.multiTenant = multiTenant;

         for(IdentityID identity : identities) {
            if(identity != null && identity.name != null) {
               index.computeIfAbsent(key(identity), k -> new ArrayList<>()).add(identity);
            }
         }
      }

      boolean isEmpty() {
         return index.isEmpty();
      }

      /**
       * Gets the listed identities that the database may treat as the given identity, other
       * than the identity itself. Without multi-tenancy only the name is bound, so the
       * organization ID is ignored.
       */
      List<IdentityID> candidates(IdentityID identity) {
         return index.getOrDefault(key(identity), List.of()).stream()
            .filter(other -> multiTenant ?
               !identity.equals(other) : !identity.name.equals(other.name))
            .toList();
      }

      private String key(IdentityID identity) {
         return multiTenant ?
            candidateKey(collator, identity.orgID) + '/' + candidateKey(collator, identity.name) :
            candidateKey(collator, identity.name);
      }

      private final boolean multiTenant;
      // a Collator is not thread-safe, so each index has its own
      private final Collator collator = createCandidateCollator();
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
            Ambiguity ambiguity = isAmbiguousUser(handle, user);

            if(ambiguity != Ambiguity.NONE) {
               return new QueryResult<>(new IdentityID[0], ambiguity == Ambiguity.FAILED);
            }

            IdentityID[] result = queryUserRoles(handle, user);

            if(isMergedUser(handle, user, roleNames(result),
                            other -> roleNames(queryUserRoles(handle, other)), "roles"))
            {
               return new QueryResult<>(new IdentityID[0], false);
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

      return new QueryResult<>(new IdentityID[0], true);
   }

   private IdentityID[] queryUserRoles(Handle handle, IdentityID user) {
      Query query = handle.createQuery(provider.getUserRolesQuery());

      if(provider.isMultiTenant()) {
         query.bind(0, user.orgID);
         query.bind(1, user.name);
      }
      else {
         query.bind(0, user.name);
      }

      return query.map((rs, ctx) -> mapToUserRoleIdentity(rs, ctx, user.getOrgID())).stream()
         .filter(Objects::nonNull)
         .toArray(IdentityID[]::new);
   }

   /**
    * Gets the role names for {@link #isMergedUser}. The organization ID of a role depends on
    * the organization of the user it is loaded for, so only the names are compared.
    */
   private static Set<String> roleNames(IdentityID[] roles) {
      Set<String> names = new HashSet<>();

      for(IdentityID role : roles) {
         names.add(role.name);
      }

      return names;
   }

   /**
    * Checks whether the database treats the name of the given user as the name of another
    * listed user in the user roles or user emails query. {@link #isAmbiguousUser} decides from
    * the users query, which cannot tell when it is blank, when its first column is not the
    * stored user name, or when the roles and emails tables compare names under a different
    * collation than the users table. This check asks the roles or emails query itself: a loose
    * comparison of the listed users (see {@link #candidateKey}) only finds candidates, and the
    * same query is then run for each candidate. If it returns the same non-empty values as for
    * the requested user, the database merged the two names and the values cannot be attributed
    * to one user. If the values differ, the database tells the names apart (for example on a
    * case-sensitive database) and they are used. Users without a candidate, which is the normal
    * case, run no extra roles or emails query. Without the cache, the user list query is run
    * for every lookup that returns values.
    *
    * @param values the result of the query for the requested user.
    * @param query  runs the same query for another user.
    * @param kind   "roles" or "emails", for the log message.
    *
    * @return {@code true} if the values must not be loaded.
    */
   private boolean isMergedUser(Handle handle, IdentityID user, Set<String> values,
                                Function<IdentityID, Set<String>> query, String kind)
   {
      if(values.isEmpty() || user == null || user.name == null ||
         StringUtils.isBlank(provider.getUserListQuery()))
      {
         return false;
      }

      for(IdentityID other : indexIdentities(getUserList(handle)).candidates(user)) {
         if(values.equals(query.apply(other))) {
            // without the cache every lookup of the user reaches this, so warn once per user
            String message =
               "The user {} query returned the same {} for user {} and user {}, the database " +
               "treats their names as the same name. The {} of user {} will not be loaded. " +
               "User names (and organization IDs) must be unique under the database " +
               "collation used by the user {} query, including case, accents, width, kana " +
               "type and trailing spaces.";
            String userLabel = identityLabel(user);
            Object[] args =
               { kind, kind, userLabel, identityLabel(other), kind, userLabel, kind };

            if(mergedUsers.add(kind + ":" + userLabel)) {
               LOG.warn(message, args);
            }
            else {
               LOG.debug(message, args);
            }

            return true;
         }
      }

      return false;
   }

   /**
    * Gets the user list for {@link #isMergedUser}. The cached list is used when the cache is
    * enabled, since the cached user roles and emails are reset together with it, otherwise the
    * user list query is run on the given handle. In multi-tenant mode the query is always run:
    * the cached list is a sorted set whose {@link IdentityID} order may ignore the case of the
    * organization ID, so it keeps only one of "bob" in "acme" and "bob" in "ACME", and the
    * other one would not be found as a candidate.
    */
   private List<IdentityID> getUserList(Handle handle) {
      if(provider.isCacheEnabled() && !provider.isIgnoreCache() && !provider.isMultiTenant()) {
         IdentityID[] users = provider.getUsers();

         if(users != null && users.length > 0) {
            return Arrays.asList(users);
         }
      }

      return queryUserList(handle);
   }

   private List<IdentityID> queryUserList(Handle handle) {
      return handle.createQuery(provider.getUserListQuery())
         .map(this::mapToIdentity).stream()
         .filter(Objects::nonNull)
         .toList();
   }

   public QueryResult<Map<IdentityID, IdentityArray>> getUserRoles() {
      return getUserRoles(null);
   }

   /**
    * Gets the roles of all users from the user role list query. The rows of users that the
    * database may treat as another listed user (see {@link #isMergedUser}) are left out: the
    * query may have merged them, for example when it joins the users table on the user name
    * under a case-insensitive collation, and the rows cannot be attributed to one user. The
    * roles of those users are loaded one user at a time by {@link #getRoles(IdentityID)},
    * which checks them.
    *
    * @param userList the listed users, or {@code null} to run the user list query.
    */
   public QueryResult<Map<IdentityID, IdentityArray>> getUserRoles(
      Collection<IdentityID> userList)
   {
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

            if(!userRoles.isEmpty() && !StringUtils.isBlank(provider.getUserListQuery())) {
               IdentityIndex users =
                  indexIdentities(userList == null ? queryUserList(handle) : userList);
               userRoles.keySet().removeIf(
                  user -> user != null && user.name != null && !users.candidates(user).isEmpty());
            }

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
            Ambiguity ambiguity = isAmbiguousUser(handle, user);

            if(ambiguity != Ambiguity.NONE) {
               return new QueryResult<>(new String[0], ambiguity == Ambiguity.FAILED);
            }

            String[] result = queryUserEmails(handle, user);

            if(isMergedUser(handle, user, new HashSet<>(Arrays.asList(result)),
                            other -> new HashSet<>(Arrays.asList(queryUserEmails(handle, other))),
                            "emails"))
            {
               return new QueryResult<>(new String[0], false);
            }

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

   private String[] queryUserEmails(Handle handle, IdentityID user) {
      Query query = handle.createQuery(provider.getUserEmailsQuery());

      if(provider.isMultiTenant()) {
         query.bind(0, user.orgID);
         query.bind(1, user.name);
      }
      else {
         query.bind(0, user.name);
      }

      return query.map(this::mapToEmail).stream()
         .filter(Objects::nonNull)
         .toArray(String[]::new);
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
   private final Set<String> mergedUsers = ConcurrentHashMap.newKeySet();

   private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
   private static final Logger LOG = LoggerFactory.getLogger(AuthenticationDAO.class);
}
