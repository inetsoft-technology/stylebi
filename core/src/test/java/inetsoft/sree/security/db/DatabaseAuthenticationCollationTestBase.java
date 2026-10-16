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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Shared set-up of the database provider collation tests: in-memory Derby databases, with a
 * case-insensitive collation when requested, read through the real {@link AuthenticationDAO}.
 */
abstract class DatabaseAuthenticationCollationTestBase {
   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   static void assertNoMergedRoles(Map<IdentityID, IdentityID[]> allUserRoles) {
      // bob and BOB are left out of the bulk list or have no roles, never the merged roles
      for(String name : new String[] { "bob", "BOB" }) {
         IdentityID[] userRoles = allUserRoles.get(new IdentityID(name,
            Organization.getDefaultOrganizationID()));
         assertTrue(userRoles == null || userRoles.length == 0,
                    name + " in all user roles: " + Arrays.toString(userRoles));
      }
   }

   /**
    * @param userRoleListQuery the bulk user role list query, or {@code null} for none.
    */
   DatabaseAuthenticationProvider cachedSingleTenantProvider(String userRoleListQuery)
      throws Exception
   {
      DatabaseAuthenticationProvider p = singleTenantProvider(true, true);

      if(userRoleListQuery != null) {
         p.setUserRoleListQuery(userRoleListQuery);
      }

      // The cluster loader service needs a configured SecurityEngine, so a stub stands in for
      // it and fills the replicated maps from the real DAO, as a cache load does.
      String name = "rolecollation_cache_" + (dbCounter++);
      String prefix = "DatabaseSecurity:" + name;
      Cluster cluster = Cluster.getInstance();
      DatabaseAuthenticationCacheService service = mock(DatabaseAuthenticationCacheService.class);
      when(service.isInitialized()).thenReturn(true);
      cluster.getSingletonService(prefix, DatabaseAuthenticationCacheService.class, () -> service);
      p.setProviderName(name);

      AuthenticationDAO dao = p.getDao();
      Map<String, Object> lists = cluster.getReplicatedMap(prefix + ".lists");
      lists.put("orgs", new TreeSet<>(Arrays.asList(dao.getOrganizations().result())));
      lists.put("users", new TreeSet<>(Arrays.asList(dao.getUsers().result())));
      lists.put("groups", new TreeSet<>(Arrays.asList(dao.getGroups().result())));
      lists.put("roles", new TreeSet<>(Arrays.asList(dao.getRoles().result())));
      Map<IdentityID, IdentityArray> userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
      userRoles.putAll(dao.getUserRoles().result());
      return p;
   }

   void createSingleTenantDb(boolean caseInsensitive) throws Exception {
      url = newUrl(caseInsensitive);
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob', 'pw-bob'), ('BOB', 'pw-BOB'), ('alice', 'pw-alice')",
           "INSERT INTO UR VALUES ('bob', 'role-bob'), ('BOB', 'Administrator'), " +
              "('alice', 'r-alice')",
           "INSERT INTO UE VALUES ('bob', 'bob@x'), ('BOB', 'BOB@y'), ('alice', 'alice@z')",
           "INSERT INTO G VALUES ('sales')",
           "INSERT INTO GU VALUES ('sales', 'alice')");
   }

   void createMultiTenantDb(boolean caseInsensitive) throws Exception {
      url = newUrl(caseInsensitive);
      exec("CREATE TABLE O (ID VARCHAR(50), NAME VARCHAR(50))",
           "CREATE TABLE U (ORG_ID VARCHAR(50), NAME VARCHAR(50), PW VARCHAR(50))",
           "CREATE TABLE UR (ORG_ID VARCHAR(50), USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (ORG_ID VARCHAR(50), USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "INSERT INTO O VALUES ('acme', 'Acme'), ('ACME', 'Other Tenant')",
           "INSERT INTO U VALUES ('acme', 'bob', 'pw-acme'), ('ACME', 'bob', 'pw-ACME')",
           "INSERT INTO UR VALUES ('acme', 'bob', 'r-acme'), ('ACME', 'bob', 'r-ACME')",
           "INSERT INTO UE VALUES ('acme', 'bob', 'bob@acme'), ('ACME', 'bob', 'bob@ACME')");
   }

   DatabaseAuthenticationProvider singleTenantProvider(boolean caseSensitive)
      throws Exception
   {
      return singleTenantProvider(caseSensitive, false);
   }

   DatabaseAuthenticationProvider singleTenantProvider(boolean caseSensitive,
                                                               boolean cache)
      throws Exception
   {
      DatabaseAuthenticationProvider p = newProvider(caseSensitive, cache);
      p.setMultiTenantSupplier(() -> false);
      p.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      p.setUserListQuery("SELECT NAME FROM U");
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
      p.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
      p.setGroupListQuery("SELECT GROUP_NAME FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");
      return p;
   }

   DatabaseAuthenticationProvider multiTenantProvider() throws Exception {
      return multiTenantProvider(false);
   }

   DatabaseAuthenticationProvider multiTenantProvider(boolean cache) throws Exception {
      DatabaseAuthenticationProvider p = newProvider(true, cache);
      p.setMultiTenantSupplier(() -> true);
      p.setOrganizationListQuery("SELECT ID FROM O");
      p.setOrganizationNameQuery("SELECT NAME FROM O WHERE ID = ?");
      p.setUserQuery("SELECT NAME, PW FROM U WHERE ORG_ID = ? AND NAME = ?");
      p.setUserListQuery("SELECT NAME, ORG_ID FROM U");
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE ORG_ID = ? AND USER_NAME = ?");
      p.setRoleListQuery("SELECT DISTINCT ROLE_NAME, ORG_ID FROM UR");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE ORG_ID = ? AND USER_NAME = ?");
      return p;
   }

   DatabaseAuthenticationProvider newProvider(boolean caseSensitive, boolean cache)
      throws Exception
   {
      String oldCaseSensitive = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      DatabaseAuthenticationProvider p;

      try {
         SreeEnv.setProperty("security.cache", Boolean.toString(cache));
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         p = new DatabaseAuthenticationProvider();
      }
      finally {
         restoreProperty("security.cache", oldCache);
         restoreProperty("security.user.caseSensitive", oldCaseSensitive);
      }

      p.setRequiresLogin(false);
      p.setHashAlgorithm("None");
      ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
      when(connectionProvider.getConnection()).thenAnswer(inv -> DriverManager.getConnection(url));
      Field field = DatabaseAuthenticationProvider.class.getDeclaredField("connectionProvider");
      field.setAccessible(true);
      field.set(p, connectionProvider);
      provider = p;
      return p;
   }

   static void restoreProperty(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   static String newUrl(boolean caseInsensitive) {
      return "jdbc:derby:memory:rolecollation_" + (dbCounter++) + ";create=true" +
         (caseInsensitive ? ";territory=en_US;collation=TERRITORY_BASED:PRIMARY" : "");
   }

   void exec(String... sql) throws Exception {
      try(Connection connection = DriverManager.getConnection(url);
          Statement statement = connection.createStatement())
      {
         for(String query : sql) {
            statement.execute(query);
         }
      }
   }

   IdentityID id(String name) {
      return new IdentityID(name, Organization.getDefaultOrganizationID());
   }

   boolean login(AuthenticationProvider p, String name, String password) {
      IdentityID user = id(name);
      return p.authenticate(user, new DefaultTicket(user, password));
   }

   static List<String> roles(IdentityID[] ids) {
      assertNotNull(ids);
      return Arrays.stream(ids).map(i -> i.name).sorted().toList();
   }

   static List<String> qualifiedRoles(IdentityID[] ids) {
      assertNotNull(ids);
      return Arrays.stream(ids).map(i -> i.name + "@" + i.orgID).sorted().toList();
   }

   static List<String> sorted(String[] values) {
      assertNotNull(values);
      return Arrays.stream(values).sorted().toList();
   }

   // joins the users table on the name, so a case-insensitive collation merges the rows of
   // bob and BOB under both names
   static final String JOINED_USER_ROLE_LIST =
      "SELECT U.NAME, UR.ROLE_NAME FROM U JOIN UR ON U.NAME = UR.USER_NAME";
   private static int dbCounter = 0;
   private String url;
   private DatabaseAuthenticationProvider provider;
}
