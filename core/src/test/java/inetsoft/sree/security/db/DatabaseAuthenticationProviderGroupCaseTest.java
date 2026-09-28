/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77132: with <tt>security.user.caseSensitive=false</tt>, a group member row must only be
 * matched ignoring case to the one stored user it resolves to, so user <tt>bob</tt> never gets
 * the groups of the different stored user <tt>BOB</tt>. Runs against an in-memory Derby
 * database with its default, case sensitive, collation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationProviderGroupCaseTest {
   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @Test
   void getUser_caseInsensitive_groupMembersMatchOnlyTheOneStoredUser() throws Exception {
      createSingleTenantDatabase();
      CountingProvider p = directProvider(false, false);

      assertGroups(p, "bob", "sales");
      assertGroups(p, "BOB", "ADMINS");
      // a member row with a different case of a single stored user still matches it
      assertGroups(p, "carol", "support");
      assertGroups(p, "alice");
      // "Bob" matches both stored bob and BOB ignoring case: no user, and neither gets "amb"
      assertNull(p.getUser(id("Bob")));
   }

   @Test
   void getUser_caseSensitive_groupMembersMatchExactly() throws Exception {
      createSingleTenantDatabase();
      CountingProvider p = directProvider(true, false);

      assertGroups(p, "bob", "sales");
      assertGroups(p, "BOB", "ADMINS");
      assertGroups(p, "carol");
      assertNull(p.getUser(id("Bob")));
   }

   @Test
   void getUser_caseInsensitive_readsUserListAtMostTwice() throws Exception {
      createSingleTenantDatabase();
      CountingProvider p = directProvider(false, false);

      for(String name : new String[] { "bob", "BOB", "carol", "dave", "alice" }) {
         p.users.set(0);
         assertNotNull(p.getUser(id(name)), name);
         // one list for the stored id, one for the group members, however many variant or
         // orphan member rows there are
         assertTrue(p.users.get() <= 2, name + ": getUsers() calls = " + p.users.get());
      }
   }

   @Test
   void getUser_caseInsensitive_multiTenant_membersStayInTheirOrganization() throws Exception {
      url = nextUrl("mt");
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, ORG VARCHAR(50), PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ORG VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), ORG VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50), ORG VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), ORG VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob','o1','x'),('BOB','o1','x'),('carol','o1','x')," +
              "('eve','o2','x'),('frank','o1','x'),('FRANK','o2','x')",
           "INSERT INTO G VALUES ('sales','o1'),('ADMINS','o1'),('support','o1'),('x1','o1')," +
              "('y2','o2'),('f1','o1')",
           "INSERT INTO GU VALUES ('sales','o1','bob'),('ADMINS','o1','BOB')," +
              "('support','o1','Carol'),('x1','o1','EVE'),('y2','o2','Carol'),('f1','o1','Frank')");
      CountingProvider p = directProvider(false, true);
      p.setUserQuery("SELECT NAME, PW FROM U WHERE ORG = ? AND NAME = ?");
      p.setUserListQuery("SELECT NAME, ORG FROM U");
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE ORG = ? AND USER_NAME = ?");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE ORG = ? AND USER_NAME = ?");
      p.setGroupListQuery("SELECT GROUP_NAME, ORG FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE ORG = ? AND GROUP_NAME = ?");

      assertGroups(p, new IdentityID("bob", "o1"), "sales");
      assertGroups(p, new IdentityID("BOB", "o1"), "ADMINS");
      assertGroups(p, new IdentityID("carol", "o1"), "support");
      assertGroups(p, new IdentityID("eve", "o2"));
      assertGroups(p, new IdentityID("frank", "o1"), "f1");
      assertGroups(p, new IdentityID("FRANK", "o2"));
   }

   @Test
   void checkPermission_caseInsensitive_cacheOn_doesNotGrantOtherStoredUsersGroups()
      throws Exception
   {
      url = nextUrl("cache");
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob','x'),('BOB','x'),('alice','x'),('carol','x')",
           "INSERT INTO G VALUES ('sales'),('ADMINS'),('support')",
           "INSERT INTO GU VALUES ('sales','bob'),('ADMINS','BOB'),('support','Carol')");
      String oldCs = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      String oldMt = SreeEnv.getProperty("security.users.multiTenant");

      try {
         SreeEnv.setProperty("security.cache", "true");
         SreeEnv.setProperty("security.user.caseSensitive", "false");
         SreeEnv.setProperty("security.users.multiTenant", "false");

         String name = "GroupCase77132";
         DatabaseAuthenticationProvider cfg = new DatabaseAuthenticationProvider();
         cfg.setDriver(DRIVER);
         cfg.setUrl(url);
         configureQueries(cfg);
         cfg.setProviderName(name);
         AuthenticationChain authc = new AuthenticationChain();
         authc.setProviders(List.of(cfg));
         authc.saveConfiguration();
         FileAuthorizationProvider authz = new FileAuthorizationProvider();
         authz.setProviderName("Primary");
         AuthorizationChain authzChain = new AuthorizationChain();
         authzChain.setProviders(List.of(authz));
         authzChain.saveConfiguration();
         SreeEnv.setProperty("security.enabled", "true");
         SreeEnv.save();
         SecurityEngine.getSecurity().init();

         SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();
         DatabaseAuthenticationProvider p = find(root, name);
         p.setMultiTenantSupplier(() -> false);
         p.setDriverAvailable(d -> true);
         p.setDriverSupplier(d -> (Driver) Class.forName(DRIVER).getConstructor().newInstance());
         provider = p;
         p.getCache(true).load();
         Awaitility.await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofMillis(100))
            .until(p::isCacheInitialized);

         Permission adm = new Permission();
         adm.setGroupGrantsForOrg(ResourceAction.READ, Set.of("ADMINS"), org);
         root.setPermission(ResourceType.REPORT, "GroupCase77132/adm", adm, org);
         Permission sup = new Permission();
         sup.setGroupGrantsForOrg(ResourceAction.READ, Set.of("support"), org);
         root.setPermission(ResourceType.REPORT, "GroupCase77132/sup", sup, org);

         SRPrincipal bob = principal(root, "bob");
         assertFalse(root.checkPermission(
            bob, ResourceType.REPORT, "GroupCase77132/adm", ResourceAction.READ));
         // the permission check merges getUser()'s groups into the session principal
         assertEquals(List.of("sales"), sorted(bob.getGroups()));

         // a member row with a different case of a single stored user still grants
         SRPrincipal carol = principal(root, "carol");
         assertTrue(root.checkPermission(
            carol, ResourceType.REPORT, "GroupCase77132/sup", ResourceAction.READ));

         SRPrincipal alice = principal(root, "alice");
         assertFalse(root.checkPermission(
            alice, ResourceType.REPORT, "GroupCase77132/adm", ResourceAction.READ));
      }
      finally {
         restore("security.cache", oldCache);
         restore("security.user.caseSensitive", oldCs);
         restore("security.users.multiTenant", oldMt);
      }
   }

   private void createSingleTenantDatabase() throws Exception {
      url = nextUrl("st");
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob','x'),('BOB','x'),('alice','x'),('carol','x'),('dave','x')",
           "INSERT INTO G VALUES ('sales'),('ADMINS'),('support'),('amb'),('trail'),('orphan')",
           "INSERT INTO GU VALUES ('sales','bob'),('ADMINS','BOB'),('support','Carol')," +
              "('amb','Bob'),('trail','DAVE '),('orphan','zed')");
   }

   private SRPrincipal principal(SecurityProvider root, String name) {
      // as at log in: the groups come from the exact, one argument lookup
      return new SRPrincipal(id(name), new IdentityID[0], root.getUserGroups(id(name)), org, 7L);
   }

   private void assertGroups(DatabaseAuthenticationProvider p, String name, String... groups) {
      assertGroups(p, id(name), groups);
   }

   private void assertGroups(DatabaseAuthenticationProvider p, IdentityID userID,
                             String... groups)
   {
      User user = p.getUser(userID);
      assertNotNull(user, userID.toString());
      assertEquals(sorted(groups), sorted(user.getGroups()), userID.toString());
   }

   private void exec(String... sql) throws Exception {
      try(Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
         for(String q : sql) {
            s.execute(q);
         }
      }
   }

   private void configureQueries(DatabaseAuthenticationProvider p) {
      p.setRequiresLogin(false);
      p.setHashAlgorithm("None");
      p.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      p.setUserListQuery("SELECT NAME FROM U");
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
      p.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
      p.setUserRoleListQuery("SELECT USER_NAME, ROLE_NAME FROM UR");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
      p.setGroupListQuery("SELECT GROUP_NAME FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");
      p.setSystemAdministratorRoles(new String[] { "Site Admin" });
      p.setOrgAdministratorRoles(new String[] { "Org Admin" });
   }

   private CountingProvider directProvider(boolean caseSensitive, boolean multiTenant)
      throws Exception
   {
      String oldCs = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      CountingProvider p;

      try {
         SreeEnv.setProperty("security.cache", "false");
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         p = new CountingProvider();
      }
      finally {
         restore("security.cache", oldCache);
         restore("security.user.caseSensitive", oldCs);
      }

      p.setMultiTenantSupplier(() -> multiTenant);
      configureQueries(p);
      ConnectionProvider cp = mock(ConnectionProvider.class);
      when(cp.getConnection()).thenAnswer(inv -> DriverManager.getConnection(url));
      Field f = DatabaseAuthenticationProvider.class.getDeclaredField("connectionProvider");
      f.setAccessible(true);
      f.set(p, cp);
      provider = p;
      return p;
   }

   private static DatabaseAuthenticationProvider find(SecurityProvider root, String name) {
      AuthenticationProvider a = root.getAuthenticationProvider();

      if(a instanceof DatabaseAuthenticationProvider db && name.equals(db.getProviderName())) {
         return db;
      }

      if(a instanceof AuthenticationChain chain) {
         for(AuthenticationProvider c : chain.getProviders()) {
            if(c instanceof DatabaseAuthenticationProvider db &&
               name.equals(db.getProviderName()))
            {
               return db;
            }
         }
      }

      throw new IllegalStateException("provider not found: " + name);
   }

   private static void restore(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   private IdentityID id(String name) {
      return new IdentityID(name, org);
   }

   private static List<String> sorted(String[] values) {
      List<String> result = new ArrayList<>(Arrays.asList(values));
      Collections.sort(result);
      return result;
   }

   private static String nextUrl(String tag) {
      return "jdbc:derby:memory:groupcase77132_" + tag + "_" + (dbCounter++) + ";create=true";
   }

   private static final class CountingProvider extends DatabaseAuthenticationProvider {
      @Override
      public IdentityID[] getUsers() {
         users.incrementAndGet();
         return super.getUsers();
      }

      private final AtomicInteger users = new AtomicInteger();
   }

   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static int dbCounter = 0;
   private String url;
   private DatabaseAuthenticationProvider provider;
   private final String org = Organization.getDefaultOrganizationID();
}
