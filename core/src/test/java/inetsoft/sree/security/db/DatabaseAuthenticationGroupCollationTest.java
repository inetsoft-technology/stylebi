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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.sql.*;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78251: the group users query binds a group name, so when the database collation treats
 * two listed group names as the same name (for example "sales" and "SALES" under a
 * case-insensitive collation, "sales" and "sal&eacute;s" under an accent-insensitive one, or
 * "sales" and "sales " under PAD SPACE comparison), it returns the members of both groups for
 * either name. The database provider must then load no members for those groups, instead of
 * giving each group the members of the other, while groups that the database tells apart keep
 * their own members. Runs against in-memory Derby databases, using the real
 * {@link AuthenticationDAO}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseAuthenticationGroupCollationTest {
   @BeforeEach
   void savePrincipal() {
      oldPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(oldPrincipal);

      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   @ParameterizedTest(name = "caseSensitive={0}")
   @ValueSource(booleans = { true, false })
   void caseVariantGroups_caseInsensitiveDatabase_membersNotLoaded(boolean caseSensitive)
      throws Exception
   {
      createGroupDb(true, "sales", "SALES");
      DatabaseAuthenticationProvider p = directProvider(caseSensitive);

      assertMembersNotLoaded(p, "sales", "SALES");
      assertSupportUnchanged(p);
   }

   @ParameterizedTest(name = "caseSensitive={0}")
   @ValueSource(booleans = { true, false })
   void caseVariantGroups_caseSensitiveDatabase_membersUnchanged(boolean caseSensitive)
      throws Exception
   {
      createGroupDb(false, "sales", "SALES");
      DatabaseAuthenticationProvider p = directProvider(caseSensitive);

      assertMembersUnchanged(p, "sales", "SALES");
      assertSupportUnchanged(p);
   }

   @Test
   void trailingSpaceGroups_caseSensitiveDatabase_membersNotLoaded() throws Exception {
      // Derby compares with PAD SPACE semantics even under its default collation
      createGroupDb(false, "sales", "sales ");
      DatabaseAuthenticationProvider p = directProvider(true);

      assertMembersNotLoaded(p, "sales", "sales ");
      assertSupportUnchanged(p);
   }

   @Test
   void accentVariantGroups_accentInsensitiveDatabase_membersNotLoaded() throws Exception {
      // TERRITORY_BASED:PRIMARY ignores accents as well as case
      createGroupDb(true, "sales", ACCENTED);
      DatabaseAuthenticationProvider p = directProvider(true);

      assertMembersNotLoaded(p, "sales", ACCENTED);
      assertSupportUnchanged(p);
   }

   @Test
   void accentVariantGroups_caseSensitiveDatabase_membersUnchanged() throws Exception {
      createGroupDb(false, "sales", ACCENTED);
      DatabaseAuthenticationProvider p = directProvider(true);

      assertMembersUnchanged(p, "sales", ACCENTED);
      assertSupportUnchanged(p);
   }

   @Test
   void groupNotListedExactly_caseInsensitiveDatabase_membersNotLoaded() throws Exception {
      createGroupDb(true, "sales", "SALES");
      DatabaseAuthenticationProvider p = directProvider(true);

      // a name that is not listed but that the database matches to both listed groups
      assertEquals(List.of(), names(p.getUsers(id("Sales"))));
   }

   @Test
   void caseVariantOrganizations_caseInsensitiveDatabase_membersNotLoaded() throws Exception {
      url = newUrl(true);
      exec("CREATE TABLE U (ORG_ID VARCHAR(50), NAME VARCHAR(50), PW VARCHAR(50))",
           "CREATE TABLE G (ORG_ID VARCHAR(50), GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (ORG_ID VARCHAR(50), GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('acme', 'bob', 'x'), ('ACME', 'eve', 'x'), " +
              "('acme', 'alice', 'x')",
           "INSERT INTO G VALUES ('acme', 'sales'), ('ACME', 'sales'), ('acme', 'support')",
           "INSERT INTO GU VALUES ('acme', 'sales', 'bob'), ('ACME', 'sales', 'eve'), " +
              "('acme', 'support', 'alice')");
      DatabaseAuthenticationProvider p = directProvider(true);
      p.setMultiTenantSupplier(() -> true);
      p.setUserQuery("SELECT NAME, PW FROM U WHERE ORG_ID = ? AND NAME = ?");
      p.setUserListQuery("SELECT NAME, ORG_ID FROM U");
      p.setGroupListQuery("SELECT GROUP_NAME, ORG_ID FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE ORG_ID = ? AND GROUP_NAME = ?");

      assertEquals(List.of(), names(p.getUsers(new IdentityID("sales", "acme"))));
      assertEquals(List.of(), names(p.getUsers(new IdentityID("sales", "ACME"))));
      assertEquals(List.of("alice"), names(p.getUsers(new IdentityID("support", "acme"))));
   }

   @Test
   void caseVariantOrganizations_caseSensitiveDatabase_membersUnchanged() throws Exception {
      url = newUrl(false);
      exec("CREATE TABLE G (ORG_ID VARCHAR(50), GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (ORG_ID VARCHAR(50), GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO G VALUES ('acme', 'sales'), ('ACME', 'sales')",
           "INSERT INTO GU VALUES ('acme', 'sales', 'bob'), ('ACME', 'sales', 'eve')");
      DatabaseAuthenticationProvider p = directProvider(true);
      p.setMultiTenantSupplier(() -> true);
      p.setGroupListQuery("SELECT GROUP_NAME, ORG_ID FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE ORG_ID = ? AND GROUP_NAME = ?");

      assertEquals(List.of("bob"), names(p.getUsers(new IdentityID("sales", "acme"))));
      assertEquals(List.of("eve"), names(p.getUsers(new IdentityID("sales", "ACME"))));
   }

   @ParameterizedTest(name = "cache={0}")
   @ValueSource(booleans = { false, true })
   void caseVariantGroups_emailsAndPermissionsNotMerged(boolean cache) throws Exception {
      createGroupDb(true, "sales", "SALES");
      String oldCaseSensitive = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      String oldMultiTenant = SreeEnv.getProperty("security.users.multiTenant");

      try {
         String name = "GroupCollation78251_" + cache;
         SecurityProvider root = initSecurityEngine(name, cache);

         // email to a group, as a schedule task or the viewsheet email dialog addresses it
         assertEquals(List.of(), groupEmails("sales"), "emails of sales");
         assertEquals(List.of(), groupEmails("SALES"), "emails of SALES");
         assertEquals(List.of("alice@z"), groupEmails("support"), "emails of support");

         // groups of the principal at log in
         assertEquals(List.of(), sorted(root.getUserGroups(id("eve"))), "eve groups");
         assertEquals(List.of(), sorted(root.getUserGroups(id("bob"))), "bob groups");
         assertEquals(List.of("support"), sorted(root.getUserGroups(id("alice"))));

         Permission salesOnly = new Permission();
         salesOnly.setGroupGrantsForOrg(ResourceAction.READ, Set.of("sales"), org);
         root.setPermission(ResourceType.REPORT, name + "/salesOnly", salesOnly, org);
         Permission supportOnly = new Permission();
         supportOnly.setGroupGrantsForOrg(ResourceAction.READ, Set.of("support"), org);
         root.setPermission(ResourceType.REPORT, name + "/supportOnly", supportOnly, org);

         // principals with no groups: the permission check adds getUser()'s groups itself
         for(String user : new String[] { "eve", "bob", "alice" }) {
            assertFalse(root.checkPermission(principal(user), ResourceType.REPORT,
                                             name + "/salesOnly", ResourceAction.READ),
                        user + " has the grant of sales");
         }

         assertTrue(root.checkPermission(principal("alice"), ResourceType.REPORT,
                                         name + "/supportOnly", ResourceAction.READ));
      }
      finally {
         restoreProperty("security.cache", oldCache);
         restoreProperty("security.user.caseSensitive", oldCaseSensitive);
         restoreProperty("security.users.multiTenant", oldMultiTenant);
      }
   }

   @ParameterizedTest(name = "cache={0}")
   @ValueSource(booleans = { false, true })
   void caseVariantGroups_caseSensitiveDatabase_emailsAndPermissionsUnchanged(boolean cache)
      throws Exception
   {
      createGroupDb(false, "sales", "SALES");
      // an unambiguous group with many members
      StringBuilder users = new StringBuilder("INSERT INTO U VALUES ");
      StringBuilder emails = new StringBuilder("INSERT INTO UE VALUES ");
      StringBuilder members = new StringBuilder("INSERT INTO GU VALUES ('eng', 'alice')");

      for(int i = 0; i < 60; i++) {
         String sep = i == 0 ? "" : ", ";
         users.append(sep).append("('m").append(i).append("', 'pw-m").append(i).append("')");
         emails.append(sep).append("('m").append(i).append("', 'm").append(i).append("@e')");
         members.append(", ('eng', 'm").append(i).append("')");
      }

      exec(users.toString(), emails.toString(), members.toString(),
           "INSERT INTO G VALUES ('eng')");
      String oldCaseSensitive = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      String oldMultiTenant = SreeEnv.getProperty("security.users.multiTenant");

      try {
         String name = "GroupCollationCS78251_" + cache;
         SecurityProvider root = initSecurityEngine(name, cache);

         assertEquals(63, root.getUsers().length);
         assertEquals(List.of("bob@x"), groupEmails("sales"), "emails of sales");
         assertEquals(List.of("eve@y"), groupEmails("SALES"), "emails of SALES");
         assertEquals(61, groupEmails("eng").size(), "emails of eng");
         assertEquals(List.of("sales"), sorted(root.getUserGroups(id("bob"))), "bob groups");
         assertEquals(List.of("SALES"), sorted(root.getUserGroups(id("eve"))), "eve groups");
         assertEquals(List.of("eng", "support"), sorted(root.getUserGroups(id("alice"))));

         for(String user : new String[] { "bob", "eve", "alice", "m7" }) {
            IdentityID userID = id(user);
            assertTrue(root.authenticate(userID, new DefaultTicket(userID, "pw-" + user)),
                       "login of " + user);
         }

         Permission salesOnly = new Permission();
         salesOnly.setGroupGrantsForOrg(ResourceAction.READ, Set.of("sales"), org);
         root.setPermission(ResourceType.REPORT, name + "/salesOnly", salesOnly, org);
         Permission engOnly = new Permission();
         engOnly.setGroupGrantsForOrg(ResourceAction.READ, Set.of("eng"), org);
         root.setPermission(ResourceType.REPORT, name + "/engOnly", engOnly, org);

         // eve is not checked against the sales grant: Permission.check falls back to a
         // case-insensitive identity match, independent of the database provider
         assertTrue(root.checkPermission(principal("bob"), ResourceType.REPORT,
                                         name + "/salesOnly", ResourceAction.READ));
         assertTrue(root.checkPermission(principal("m7"), ResourceType.REPORT,
                                         name + "/engOnly", ResourceAction.READ));
         assertFalse(root.checkPermission(principal("bob"), ResourceType.REPORT,
                                          name + "/engOnly", ResourceAction.READ));
      }
      finally {
         restoreProperty("security.cache", oldCache);
         restoreProperty("security.user.caseSensitive", oldCaseSensitive);
         restoreProperty("security.users.multiTenant", oldMultiTenant);
      }
   }

   private SecurityProvider initSecurityEngine(String name, boolean cache) throws Exception {
      SreeEnv.setProperty("security.cache", Boolean.toString(cache));
      SreeEnv.setProperty("security.user.caseSensitive", "true");
      SreeEnv.setProperty("security.users.multiTenant", "false");

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
      assertEquals(cache, p.isCacheEnabled());

      if(cache) {
         p.getCache(true).load();
         Awaitility.await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofMillis(100))
            .until(p::isCacheInitialized);
      }

      return root;
   }

   private void assertMembersNotLoaded(DatabaseAuthenticationProvider p, String group1,
                                       String group2)
   {
      for(String group : new String[] { group1, group2 }) {
         assertEquals(List.of(), names(p.getUsers(id(group))), "getUsers(" + group + ")");
         assertEquals(List.of(), memberNames(p, group), "getGroupMembers(" + group + ")");
      }

      for(String user : new String[] { "bob", "eve" }) {
         assertEquals(List.of(), sorted(p.getUserGroups(id(user))), "getUserGroups(" + user + ")");
         User stored = p.getUser(id(user));
         assertNotNull(stored, user);
         assertEquals(List.of(), sorted(stored.getGroups()), "getUser(" + user + ").getGroups()");
      }
   }

   private void assertMembersUnchanged(DatabaseAuthenticationProvider p, String bobGroup,
                                       String eveGroup)
   {
      assertEquals(List.of("bob"), names(p.getUsers(id(bobGroup))));
      assertEquals(List.of("eve"), names(p.getUsers(id(eveGroup))));
      assertEquals(List.of("bob"), memberNames(p, bobGroup));
      assertEquals(List.of("eve"), memberNames(p, eveGroup));
      assertEquals(List.of(bobGroup), sorted(p.getUserGroups(id("bob"))));
      assertEquals(List.of(eveGroup), sorted(p.getUserGroups(id("eve"))));
      assertEquals(List.of(bobGroup), sorted(p.getUser(id("bob")).getGroups()));
      assertEquals(List.of(eveGroup), sorted(p.getUser(id("eve")).getGroups()));
   }

   private void assertSupportUnchanged(DatabaseAuthenticationProvider p) {
      assertEquals(List.of("alice"), names(p.getUsers(id("support"))));
      assertEquals(List.of("alice"), memberNames(p, "support"));
      assertEquals(List.of("support"), sorted(p.getUserGroups(id("alice"))));
      assertEquals(List.of("support"), sorted(p.getUser(id("alice")).getGroups()));
   }

   private List<String> memberNames(DatabaseAuthenticationProvider p, String group) {
      return Arrays.stream(p.getGroupMembers(id(group)))
         .map(Identity::getName)
         .sorted()
         .toList();
   }

   private List<String> groupEmails(String group) throws Exception {
      return sorted(SUtil.getEmails(new IdentityID(group + Identity.GROUP_SUFFIX, org)));
   }

   private SRPrincipal principal(String user) {
      return new SRPrincipal(id(user), new IdentityID[0], new String[0], org, 7L);
   }

   private void createGroupDb(boolean caseInsensitive, String bobGroup, String eveGroup)
      throws Exception
   {
      url = newUrl(caseInsensitive);
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob', 'pw-bob'), ('eve', 'pw-eve'), ('alice', 'pw-alice')",
           "INSERT INTO UR VALUES ('bob', 'r-bob'), ('eve', 'r-eve'), ('alice', 'r-alice')",
           "INSERT INTO UE VALUES ('bob', 'bob@x'), ('eve', 'eve@y'), ('alice', 'alice@z')",
           "INSERT INTO G VALUES ('" + bobGroup + "'), ('" + eveGroup + "'), ('support')",
           "INSERT INTO GU VALUES ('" + bobGroup + "', 'bob'), ('" + eveGroup + "', 'eve'), " +
              "('support', 'alice')");
   }

   private void configureQueries(DatabaseAuthenticationProvider p) {
      p.setRequiresLogin(false);
      p.setHashAlgorithm("None");
      p.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      p.setUserListQuery("SELECT NAME FROM U");
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
      p.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
      p.setGroupListQuery("SELECT GROUP_NAME FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");
      p.setSystemAdministratorRoles(new String[] { "Site Admin" });
      p.setOrgAdministratorRoles(new String[] { "Org Admin" });
   }

   private DatabaseAuthenticationProvider directProvider(boolean caseSensitive)
      throws Exception
   {
      String oldCaseSensitive = SreeEnv.getProperty("security.user.caseSensitive");
      String oldCache = SreeEnv.getProperty("security.cache");
      DatabaseAuthenticationProvider p;

      try {
         SreeEnv.setProperty("security.cache", "false");
         SreeEnv.setProperty("security.user.caseSensitive", Boolean.toString(caseSensitive));
         p = new DatabaseAuthenticationProvider();
      }
      finally {
         restoreProperty("security.cache", oldCache);
         restoreProperty("security.user.caseSensitive", oldCaseSensitive);
      }

      p.setMultiTenantSupplier(() -> false);
      configureQueries(p);
      ConnectionProvider connectionProvider = mock(ConnectionProvider.class);
      when(connectionProvider.getConnection()).thenAnswer(inv -> DriverManager.getConnection(url));
      Field field = DatabaseAuthenticationProvider.class.getDeclaredField("connectionProvider");
      field.setAccessible(true);
      field.set(p, connectionProvider);
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

   private static void restoreProperty(String name, String value) {
      if(value == null) {
         SreeEnv.remove(name);
      }
      else {
         SreeEnv.setProperty(name, value);
      }
   }

   private static String newUrl(boolean caseInsensitive) {
      return "jdbc:derby:memory:groupcollation_" + (dbCounter++) + ";create=true" +
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
      return new IdentityID(name, org);
   }

   private static List<String> names(IdentityID[] ids) {
      assertNotNull(ids);
      return Arrays.stream(ids).map(i -> i.name).sorted().toList();
   }

   private static List<String> sorted(String[] values) {
      assertNotNull(values);
      return Arrays.stream(values).sorted().toList();
   }

   private static final String ACCENTED = "salés";
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static int dbCounter = 0;
   private String url;
   private DatabaseAuthenticationProvider provider;
   private Principal oldPrincipal;
   private final String org = Organization.getDefaultOrganizationID();
}
