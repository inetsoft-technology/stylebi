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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77108: when a case-insensitive database collation makes the name of one user match the
 * rows of other users (for example "bob" and "BOB", or organization IDs "acme" and "ACME"), the
 * user roles and user emails queries return the rows of all those users. The database provider
 * must then load no roles and no emails for the ambiguous name, instead of merging them. Runs
 * against in-memory Derby databases with a case-insensitive collation, using the real
 * {@link AuthenticationDAO}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationRoleCollationTest {
   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @ParameterizedTest(name = "caseSensitive={0}")
   @ValueSource(booleans = { true, false })
   void caseVariantUsers_rolesAndEmailsNotMerged(boolean caseSensitive) throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(caseSensitive);
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      for(String name : new String[] { "bob", "BOB" }) {
         assertEquals(List.of(), roles(p.getRoles(id(name))), name + " roles");
         assertEquals(List.of(), sorted(p.getEmails(id(name))), name + " emails");
         assertEquals(List.of(), roles(chain.getRoles(id(name))), name + " chain roles");
         assertEquals(List.of(), sorted(chain.getEmails(id(name))), name + " chain emails");
      }

      User bob = p.getUser(id("bob"));
      assertNotNull(bob);
      assertEquals(List.of(), roles(bob.getRoles()), "getUser(bob) roles");
      assertEquals(List.of(), sorted(bob.getEmails()), "getUser(bob) emails");

      // an unambiguous user on the same database is unchanged
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));

      // #77106: both variants still log in with their own password
      assertTrue(login(p, "bob", "pw-bob"));
      assertTrue(login(p, "BOB", "pw-BOB"));
   }

   @Test
   void caseSensitiveDatabase_rolesAndEmailsUnchanged() throws Exception {
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);

      assertEquals(List.of("role-bob"), roles(p.getRoles(id("bob"))));
      assertEquals(List.of("Administrator"), roles(p.getRoles(id("BOB"))));
      assertEquals(List.of("bob@x"), sorted(p.getEmails(id("bob"))));
      assertEquals(List.of("BOB@y"), sorted(p.getEmails(id("BOB"))));
      assertEquals(List.of("role-bob"), roles(p.getUser(id("bob")).getRoles()));
   }

   @Test
   void transformedNameColumn_rolesAndEmailsNotMerged() throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      // column 1 is the same for both rows, the credentials tell the users apart
      p.setUserQuery("SELECT UPPER(NAME), PW FROM U WHERE NAME = ?");

      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
      assertEquals(List.of(), sorted(p.getEmails(id("bob"))));
      assertEquals(List.of(), roles(p.getUser(id("bob")).getRoles()));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
   }

   @Test
   void duplicateRowsOfOneUser_rolesUnchanged() throws Exception {
      createSingleTenantDb(true);
      exec("INSERT INTO UR VALUES ('alice', 'r-alice2')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      // the join returns one identical row per role of the user
      p.setUserQuery("SELECT U.NAME, U.PW FROM U, UR WHERE U.NAME = UR.USER_NAME AND U.NAME = ?");

      assertEquals(List.of("r-alice", "r-alice2"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
   }

   @Test
   void caseInsensitiveUsersQuery_distinctUsersFailClosed() throws Exception {
      // Deliberate: when the users query itself treats "bob" and "BOB" as one name, the roles
      // and emails of both are withheld, even on a case-sensitive database.
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserQuery("SELECT NAME, PW FROM U WHERE UPPER(NAME) = UPPER(?)");

      assertEquals(List.of(), roles(p.getRoles(id("bob"))));
      assertEquals(List.of(), roles(p.getRoles(id("BOB"))));
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertTrue(login(p, "bob", "pw-bob"));
   }

   @Test
   void blankUsersQuery_checkSkipped() throws Exception {
      createSingleTenantDb(false);
      DatabaseAuthenticationProvider p = singleTenantProvider(true);
      p.setUserQuery("");

      assertEquals(List.of("role-bob"), roles(p.getRoles(id("bob"))));
      assertEquals(List.of("bob@x"), sorted(p.getEmails(id("bob"))));
   }

   @Test
   void caseVariantOrganizations_rolesAndEmailsNotMerged() throws Exception {
      createMultiTenantDb(true);
      DatabaseAuthenticationProvider p = multiTenantProvider();

      for(String org : new String[] { "acme", "ACME" }) {
         IdentityID bob = new IdentityID("bob", org);
         assertEquals(List.of(), roles(p.getRoles(bob)), "roles of bob@" + org);
         assertEquals(List.of(), sorted(p.getEmails(bob)), "emails of bob@" + org);
      }
   }

   @Test
   void caseSensitiveMultiTenant_rolesAndEmailsUnchanged() throws Exception {
      createMultiTenantDb(false);
      DatabaseAuthenticationProvider p = multiTenantProvider();
      IdentityID bob = new IdentityID("bob", "acme");

      assertEquals(List.of("r-acme@acme"), qualifiedRoles(p.getRoles(bob)));
      assertEquals(List.of("bob@acme"), sorted(p.getEmails(bob)));
   }

   @ParameterizedTest(name = "userRoleListQuery={0}")
   @ValueSource(booleans = { false, true })
   void cacheEnabled_rolesAndEmailsNotMerged(boolean userRoleList) throws Exception {
      createSingleTenantDb(true);
      DatabaseAuthenticationProvider p = cachedSingleTenantProvider(userRoleList);
      assertTrue(p.isCacheEnabled());
      AuthenticationChain chain = new AuthenticationChain();
      chain.setProviders(List.of(p));

      // Without the bulk user role list, the cache loads the roles of each user through the
      // checked DAO lookup. With it, the preloaded roles are keyed by the exact user name.
      List<String> bobRoles = userRoleList ? List.of("role-bob") : List.of();
      List<String> upperBobRoles = userRoleList ? List.of("Administrator") : List.of();

      for(int i = 0; i < 2; i++) {
         assertEquals(bobRoles, roles(p.getRoles(id("bob"))), "bob roles, call " + i);
         assertEquals(upperBobRoles, roles(p.getRoles(id("BOB"))), "BOB roles, call " + i);
         assertEquals(bobRoles, roles(chain.getRoles(id("bob"))), "bob chain roles, call " + i);
         assertEquals(List.of(), sorted(p.getEmails(id("bob"))), "bob emails, call " + i);
         assertEquals(List.of(), sorted(p.getEmails(id("BOB"))), "BOB emails, call " + i);
      }

      User bob = p.getUser(id("bob"));
      assertNotNull(bob);
      assertEquals(bobRoles, roles(bob.getRoles()), "getUser(bob) roles");
      assertEquals(List.of(), sorted(bob.getEmails()), "getUser(bob) emails");
      assertEquals(List.of("r-alice"), roles(p.getRoles(id("alice"))));
      assertEquals(List.of("alice@z"), sorted(p.getEmails(id("alice"))));
   }

   @Test
   void unambiguousUsers_rolesUnchanged() throws Exception {
      createSingleTenantDb(true);
      exec("INSERT INTO U VALUES ('dave', 'pw-dave')",
           "INSERT INTO UR VALUES ('carol', 'r-carol')",
           "INSERT INTO UE VALUES ('carol', 'carol@z')");
      DatabaseAuthenticationProvider p = singleTenantProvider(true);

      // a user with no row in the users query is not ambiguous
      assertEquals(List.of("r-carol"), roles(p.getRoles(id("carol"))));
      assertEquals(List.of("carol@z"), sorted(p.getEmails(id("carol"))));
      // a user with no roles and no emails is unchanged
      assertEquals(List.of(), roles(p.getRoles(id("dave"))));
      assertEquals(List.of(), sorted(p.getEmails(id("dave"))));
      assertTrue(login(p, "dave", "pw-dave"));
   }

   private DatabaseAuthenticationProvider cachedSingleTenantProvider(boolean userRoleList)
      throws Exception
   {
      DatabaseAuthenticationProvider p = singleTenantProvider(true, true);

      if(userRoleList) {
         p.setUserRoleListQuery("SELECT USER_NAME, ROLE_NAME FROM UR");
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

   private void createSingleTenantDb(boolean caseInsensitive) throws Exception {
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

   private void createMultiTenantDb(boolean caseInsensitive) throws Exception {
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

   private DatabaseAuthenticationProvider singleTenantProvider(boolean caseSensitive)
      throws Exception
   {
      return singleTenantProvider(caseSensitive, false);
   }

   private DatabaseAuthenticationProvider singleTenantProvider(boolean caseSensitive,
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

   private DatabaseAuthenticationProvider multiTenantProvider() throws Exception {
      DatabaseAuthenticationProvider p = newProvider(true, false);
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

   private DatabaseAuthenticationProvider newProvider(boolean caseSensitive, boolean cache)
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

   private static void restoreProperty(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   private static String newUrl(boolean caseInsensitive) {
      return "jdbc:derby:memory:rolecollation_" + (dbCounter++) + ";create=true" +
         (caseInsensitive ? ";territory=en_US;collation=TERRITORY_BASED:PRIMARY" : "");
   }

   private void exec(String... sql) throws Exception {
      try(Connection connection = DriverManager.getConnection(url);
          Statement statement = connection.createStatement())
      {
         for(String query : sql) {
            statement.execute(query);
         }
      }
   }

   private IdentityID id(String name) {
      return new IdentityID(name, Organization.getDefaultOrganizationID());
   }

   private boolean login(AuthenticationProvider p, String name, String password) {
      IdentityID user = id(name);
      return p.authenticate(user, new DefaultTicket(user, password));
   }

   private static List<String> roles(IdentityID[] ids) {
      assertNotNull(ids);
      return Arrays.stream(ids).map(i -> i.name).sorted().toList();
   }

   private static List<String> qualifiedRoles(IdentityID[] ids) {
      assertNotNull(ids);
      return Arrays.stream(ids).map(i -> i.name + "@" + i.orgID).sorted().toList();
   }

   private static List<String> sorted(String[] values) {
      assertNotNull(values);
      return Arrays.stream(values).sorted().toList();
   }

   private static int dbCounter = 0;
   private String url;
   private DatabaseAuthenticationProvider provider;
}
