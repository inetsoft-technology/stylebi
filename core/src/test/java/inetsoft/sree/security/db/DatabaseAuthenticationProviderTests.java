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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import inetsoft.util.db.DBConnectionPool;
import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
@Tag("slow")
class DatabaseAuthenticationProviderTests {
   private DatabaseAuthenticationProvider provider;
   private static List<TestOrganization> expectedData;

   private static int testCounter = 1;
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static final String URL = "jdbc:derby:memory:authtest;create=true";
   private static final Set<String> ADMIN_ROLES = Set.of("Site Admin", "Org Admin");

   @BeforeAll
   static void loadExpectedData() throws Exception {
      Map<?, ?> map;

      try(InputStream in = DatabaseAuthenticationProviderTests.class.getResourceAsStream("multitenant.json")) {
         ObjectMapper mapper = new ObjectMapper();
         map = mapper.readValue(in, Map.class);
      }

      expectedData = new ArrayList<>();

      for(Object org : map.values()) {
         Map<?, ?> orgMap = (Map<?, ?>) org;
         String id = (String) orgMap.get("id");
         String name = (String) orgMap.get("name");
         List<TestUser> users = new ArrayList<>();
         List<String> groups = new ArrayList<>();
         List<String> roles = new ArrayList<>();
         Map<String, List<String>> userRoles = new HashMap<>();
         Map<String, List<String>> groupUsers = new HashMap<>();

         for(Object user : ((Map<?, ?>) orgMap.get("users")).values()) {
            Map<?, ?> userMap = (Map<?, ?>) user;
            String userName = (String) userMap.get("name");
            String email = (String) userMap.get("email");
            String password = (String) userMap.get("password");
            String passwordHash = (String) userMap.get("passwordHash");
            users.add(new TestUser(userName, email, password, passwordHash));
         }

         for(Object group : ((Map<?, ?>) orgMap.get("groups")).keySet()) {
            groups.add((String) group);
         }

         for(Object role : ((Map<?, ?>) orgMap.get("roles")).keySet()) {
            roles.add((String) role);
         }

         for(Map.Entry<?, ?> entry : ((Map<?, ?>) orgMap.get("userRoles")).entrySet()) {
            String userName = (String) entry.getKey();
            List<?> roleList = (List<?>) entry.getValue();
            userRoles.put(userName, roleList.stream().map(String.class::cast).toList());
         }

         for(Map.Entry<?, ?> entry : ((Map<?, ?>) orgMap.get("groupUsers")).entrySet()) {
            String groupName = (String) entry.getKey();
            List<?> userList = (List<?>) entry.getValue();
            groupUsers.put(groupName, userList.stream().map(String.class::cast).toList());
         }

         expectedData.add(new TestOrganization(id, name, users, groups, roles, userRoles, groupUsers));
      }
   }

   @BeforeEach
   void setup() throws Exception {
      loadData();
      createProvider();
   }

   @AfterEach
   void tearDown() throws Exception {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      try(Connection connection = connect()) {
         try(Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE INETSOFT_GROUP_USER");
            statement.execute("DROP TABLE INETSOFT_USER_ROLE");
            statement.execute("DROP TABLE INETSOFT_GROUP");
            statement.execute("DROP TABLE INETSOFT_ROLE");
            statement.execute("DROP TABLE INETSOFT_USER");
            statement.execute("DROP TABLE INETSOFT_ORG");
         }
      }
   }

   @ParameterizedTest
   @MethodSource("provideUsersForGetUser")
   void getUserShouldReturnValidExistingUser(IdentityID id, String name, String orgId, String email) {
      waitForCache();
      User actual = provider.getUser(id);
      assertNotNull(actual);
      assertEquals(name, actual.getName());
      assertEquals(orgId, actual.getOrganizationID());
      assertArrayEquals(new String[] { email }, actual.getEmails());
   }

   private static Stream<Arguments> provideUsersForGetUser() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(TestUser user : org.users()) {
            args.add(Arguments.of(new IdentityID(user.name(), org.id()), user.name(), org.id(), user.email()));
         }
      }

      return args.stream();
   }

   @ParameterizedTest
   @MethodSource("provideOrgsForGetOrganizations")
   void getOrganizationShouldReturnValidExistingOrganization(String id, String name) {
      waitForCache();
      Organization actual = provider.getOrganization(id);
      assertNotNull(actual);
      assertEquals(id, actual.getId());
      assertEquals(name, actual.getName());
   }

   static Stream<Arguments> provideOrgsForGetOrganizations() {
      return expectedData.stream()
         .map(o -> Arguments.of(o.id(), o.name()));
   }

   @ParameterizedTest
   @MethodSource("provideOrgsForGetOrgIdFromName")
   void getOrgIdFromNameShouldReturnCorrectValue(String name, String id) {
      waitForCache();
      String actual = provider.getOrgIdFromName(name);
      assertEquals(id, actual);
   }

   static Stream<Arguments> provideOrgsForGetOrgIdFromName() {
      return expectedData.stream()
         .map(o -> Arguments.of(o.name(), o.id()));
   }

   @ParameterizedTest
   @MethodSource("provideNamesForGetOrganizationId")
   void getOrganizationIdShouldReturnCorrectValue(String name, String id) {
      waitForCache();
      String actual = provider.getOrganizationId(name);
      assertEquals(id, actual);
   }

   static Stream<Arguments> provideNamesForGetOrganizationId() {
      return expectedData.stream()
         .map(o -> Arguments.of(o.name(), o.id()));
   }

   @ParameterizedTest
   @MethodSource("provideNamesForGetOrgNameFromID")
   void getOrgNameFromIDShouldReturnCorrectValue(String id, String name) {
      waitForCache();
      String actual = provider.getOrgNameFromID(id);
      assertEquals(name, actual);
   }

   static Stream<Arguments> provideNamesForGetOrgNameFromID() {
      return expectedData.stream()
         .map(o -> Arguments.of(o.id(), o.name()));
   }

   @Test
   void getOrganizationIdsShouldReturnCorrectValues() {
      waitForCache();
      String[] expected = expectedData.stream().map(TestOrganization::id).toArray(String[]::new);
      Arrays.sort(expected);
      String[] actual = provider.getOrganizationIDs();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   void getOrganizationNamesShouldReturnCorrectValues() {
      waitForCache();
      String[] expected = expectedData.stream().map(TestOrganization::name).toArray(String[]::new);
      Arrays.sort(expected);
      String[] actual = provider.getOrganizationNames();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @ParameterizedTest
   @MethodSource("provideNamesForGetOrganizationNames")
   void getOrganizationNameShouldReturnCorrectValue(String id, String name) {
      waitForCache();
      String actual = provider.getOrganizationName(id);
      assertEquals(name, actual);
   }

   static Stream<Arguments> provideNamesForGetOrganizationNames() {
      return expectedData.stream()
         .map(o -> Arguments.of(o.id(), o.name()));
   }

   @Test
   void getRolesShouldReturnCorrectValues() {
      waitForCache();
      List<IdentityID> idList = new ArrayList<>();

      ADMIN_ROLES.stream()
            .map(role -> new IdentityID(role, null))
            .forEach(idList::add);

      for(TestOrganization org : expectedData) {
         org.roles().stream()
            .filter(role -> !ADMIN_ROLES.contains(role))
            .forEach(role -> idList.add(new IdentityID(role, org.id())));
      }

      idList.sort(Comparator.naturalOrder());
      IdentityID[] expected = idList.toArray(new IdentityID[0]);
      IdentityID[] actual = provider.getRoles();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   void getUsersShouldReturnCorrectValues() {
      waitForCache();
      List<IdentityID> idList = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         org.users().stream()
            .map(user -> new IdentityID(user.name(), org.id()))
            .forEach(idList::add);
      }

      idList.sort(Comparator.naturalOrder());
      IdentityID[] expected = idList.toArray(new IdentityID[0]);
      IdentityID[] actual = provider.getUsers();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @ParameterizedTest
   @MethodSource("providerUsersForGetUserRoles")
   void getUserRolesShouldReturnCorrectValues(IdentityID id, IdentityID[] roles) {
      waitForCache();
      IdentityID[] actual = provider.getRoles(id);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(roles, actual);
   }

   static Stream<Arguments> providerUsersForGetUserRoles() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(Map.Entry<String, List<String>> entry: org.userRoles().entrySet()) {
            IdentityID[] roles = entry.getValue().stream()
               .map(role -> new IdentityID(role, ADMIN_ROLES.contains(role) ? null : org.id()))
               .toArray(IdentityID[]::new);
            Arrays.sort(roles);
            args.add(Arguments.of(new IdentityID(entry.getKey(), org.id()), roles));
         }
      }

      return args.stream();
   }

   @SuppressWarnings("deprecation")
   @ParameterizedTest
   @MethodSource("provideUsersForGetEmails")
   void getEmailsShouldReturnCorrectValue(IdentityID id, String[] emails) {
      waitForCache();
      String[] actual = provider.getEmails(id);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(emails, actual);
   }

   static Stream<Arguments> provideUsersForGetEmails() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(TestUser user : org.users()) {
            args.add(Arguments.of(new IdentityID(user.name(), org.id()), new String[] { user.email() }));
         }
      }

      return args.stream();
   }

   @Test
   void getIndividualUsersShouldReturnCorrectValues() {
      waitForCache();
      List<IdentityID> idList = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(TestUser user : org.users()) {
            boolean found = false;

            for(List<String> groupUsers : org.groupUsers().values()) {
               if(groupUsers.contains(user.name())) {
                  found = true;
                  break;
               }
            }

            if(!found) {
               idList.add(new IdentityID(user.name(), org.id()));
            }
         }
      }

      IdentityID[] expected = idList.toArray(new IdentityID[0]);
      Arrays.sort(expected);
      IdentityID[] actual = provider.getIndividualUsers();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @ParameterizedTest
   @MethodSource("provideRolesForGetRole")
   void getRoleShouldReturnValidExistingRole(IdentityID id, String role, String orgId) {
      waitForCache();
      Role actual = provider.getRole(id);
      assertNotNull(actual);
      assertEquals(role, actual.getName());
      assertEquals(orgId, actual.getOrganizationID());
   }

   static Stream<Arguments> provideRolesForGetRole() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(String role : org.roles()) {
            if(ADMIN_ROLES.contains(role)) {
               if("host-org".equals(org.id())) {
                  args.add(Arguments.of(new IdentityID(role, null), role, null));
               }
            }
            else {
               args.add(Arguments.of(new IdentityID(role, org.id()), role, org.id()));
            }
         }
      }

      return args.stream();
   }

   @ParameterizedTest
   @MethodSource("provideGroupsForGetGroups")
   void getGroupShouldReturnValidExistingGroup(IdentityID id, String group, String orgId) {
      waitForCache();
      Group actual = provider.getGroup(id);
      assertNotNull(actual);
      assertEquals(group, actual.getName());
      assertEquals(orgId, actual.getOrganizationID());
   }

   static Stream<Arguments> provideGroupsForGetGroups() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(String group : org.groups()) {
            args.add(Arguments.of(new IdentityID(group, org.id()), group, org.id()));
         }
      }

      return args.stream();
   }

   @Test
   void getGroupsShouldReturnCorrectValues() {
      waitForCache();
      List<IdentityID> idList = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         org.groups().stream()
            .map(group -> new IdentityID(group, org.id()))
            .forEach(idList::add);
      }

      idList.sort(Comparator.naturalOrder());
      IdentityID[] expected = idList.toArray(new IdentityID[0]);
      IdentityID[] actual = provider.getGroups();
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(expected, actual);
   }

   @ParameterizedTest
   @MethodSource("provideOrgsForGetOrganizationMembers")
   void getOrganizationMembersShouldReturnCorrectValues(String orgId, String[] users) {
      waitForCache();
      String[] actual = provider.getOrganizationMembers(orgId);
      assertNotNull(actual);
      Arrays.sort(actual);
      assertArrayEquals(users, actual);
   }

   static Stream<Arguments> provideOrgsForGetOrganizationMembers() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         String[] users = org.users().stream().map(TestUser::name).toArray(String[]::new);
         Arrays.sort(users);
         args.add(Arguments.of(org.id(), users));
      }

      return args.stream();
   }

   @ParameterizedTest
   @MethodSource("provideUsersForAuthenticate")
   void authenticateShouldSucceedWithValidCredentials(IdentityID id, DefaultTicket credential) {
      waitForCache();
      assertTrue(provider.authenticate(id, credential));
   }

   static Stream<Arguments> provideUsersForAuthenticate() {
      List<Arguments> args = new ArrayList<>();

      for(TestOrganization org : expectedData) {
         for(TestUser user : org.users()) {
            IdentityID id = new IdentityID(user.name(), org.id());
            DefaultTicket credential = new DefaultTicket(id, user.password());
            args.add(Arguments.of(id, credential));
         }
      }

      return args.stream();
   }

   private void waitForCache() {
      provider.getCache(true);
      Awaitility.await()
         .atMost(Duration.ofMinutes(1L))
         .pollInterval(Duration.ofMillis(100L))
         .until(provider::isCacheInitialized);
   }

   private void createProvider() throws Exception {
      provider = new DatabaseAuthenticationProvider();
      provider.setDriver(DRIVER);
      provider.setUrl(URL);
      provider.setRequiresLogin(false);
      provider.setUserQuery("SELECT USER_NAME, PW_HASH FROM INETSOFT_USER WHERE ORG_ID=? AND USER_NAME=?");
      provider.setUserListQuery("SELECT USER_NAME, ORG_ID FROM INETSOFT_USER");
      provider.setOrganizationListQuery("SELECT ORG_ID FROM INETSOFT_ORG");
      provider.setOrganizationNameQuery("SELECT ORG_NAME FROM INETSOFT_ORG WHERE ORG_ID=?");
      provider.setOrganizationRolesQuery("SELECT ROLE_NAME FROM INETSOFT_ROLE WHERE ORG_ID=?");
      provider.setOrganizationMembersQuery("SELECT USER_NAME FROM INETSOFT_USER WHERE ORG_ID=?");
      provider.setGroupUsersQuery("SELECT USER_NAME FROM INETSOFT_GROUP_USER WHERE ORG_ID=? AND GROUP_NAME=?");
      provider.setRoleListQuery("SELECT ROLE_NAME, ORG_ID FROM INETSOFT_ROLE");
      provider.setUserRolesQuery("SELECT ROLE_NAME FROM INETSOFT_USER_ROLE WHERE ORG_ID=? AND USER_NAME=?");
      provider.setGroupListQuery("SELECT GROUP_NAME, ORG_ID FROM INETSOFT_GROUP");
      provider.setUserEmailsQuery("SELECT EMAIL FROM INETSOFT_USER WHERE ORG_ID=? AND USER_NAME=?");
      provider.setSystemAdministratorRoles(new String[] { "Site Admin" });
      provider.setOrgAdministratorRoles(new String[] { "Org Admin" });
      provider.setHashAlgorithm("bcrypt");
      String providerName = String.format("DatabaseTest%d",  testCounter++);
      provider.setProviderName(providerName);

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(provider));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authcChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();

      SecurityEngine.getSecurity().init();

      provider = getProvider(providerName);
      provider.setMultiTenantSupplier(() -> true);
      provider.setDriverAvailable(DRIVER::equals);
      provider.setDriverSupplier(DatabaseAuthenticationProviderTests::getDriver);
   }

   private void loadData() throws Exception {
      try(Connection connection = connect();
          Statement statement = connection.createStatement())
      {
         for(String sql : getLoadStatements()) {
            statement.execute(sql);
         }
      }
   }

   private Connection connect() throws Exception {
      Class<?> clazz = getDriverClassLoader().loadClass(DRIVER);
      Driver driver = (Driver) clazz.getConstructor().newInstance();
      return driver.connect(URL, new Properties());
   }

   @SuppressWarnings("resource")
   private static ClassLoader getDriverClassLoader() {
      ClassLoader driverLoader = null;
      ClassLoader baseLoader = Thread.currentThread().getContextClassLoader();

      if(baseLoader == null) {
         baseLoader = DBConnectionPool.class.getClassLoader();
      }

      String driverDir = System.getProperty("jdbc.driver.dir");

      if(driverDir != null) {
         File[] files = new File(driverDir).listFiles();

         if(files != null) {
            List<URL> urls = new ArrayList<>();

            try {
               for(File file : files) {
                  if(file.isFile()) {
                     urls.add(file.toURI().toURL());
                  }
               }
            }
            catch(Exception e) {
               throw new RuntimeException("Failed to create classloader for directory: " + driverDir, e);
            }

            driverLoader = new URLClassLoader(urls.toArray(new URL[0]), baseLoader);
         }
      }

      return driverLoader == null ? baseLoader : driverLoader;
   }

   private static Driver getDriver(String driverClass) {
      if(!DRIVER.equals(driverClass)) {
         return null;
      }

      try {
         Class<?> clazz = getDriverClassLoader().loadClass(DRIVER);
         return (Driver) clazz.getConstructor().newInstance();
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to instantiate driver class: " + driverClass, e);
      }
   }

   private List<String> getLoadStatements() throws Exception {
      List<String> lines;

      try(InputStream in = getClass().getResourceAsStream("multitenant.sql")) {
         lines = IOUtils.readLines(Objects.requireNonNull(in), StandardCharsets.UTF_8);
      }

      List<String> statements = new ArrayList<>();
      Pattern commentPattern = Pattern.compile("^(?:--|//|#).+");
      StringBuilder command = null;

      for(String line : lines) {
         if(command == null) {
            command = new StringBuilder();
         }

         String trimmed = line.trim();
         Matcher commentMatcher = commentPattern.matcher(trimmed);

         if(!commentMatcher.find()) {
            boolean end = trimmed.endsWith(";");

            if(end) {
               command.append(trimmed, 0, trimmed.length() - 1);
               statements.add(command.toString());
               command = null;
            }
            else {
               command.append(trimmed);
            }
         }
      }

      return statements;
   }

   private DatabaseAuthenticationProvider getProvider(String providerName) {
      SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();

      if(root == null) {
         throw new IllegalStateException("Security not configured");
      }

      AuthenticationProvider rootAuthentication = root.getAuthenticationProvider();
      DatabaseAuthenticationProvider provider = null;

      if(rootAuthentication instanceof DatabaseAuthenticationProvider db &&
         Objects.equals(providerName, db.getProviderName()))
      {
         provider = db;
      }
      else if(rootAuthentication instanceof AuthenticationChain chain) {
         for(AuthenticationProvider child : chain.getProviders()) {
            if(child instanceof DatabaseAuthenticationProvider db &&
               Objects.equals(providerName, db.getProviderName()))
            {
               provider = db;
               break;
            }
         }
      }

      if(provider == null) {
         throw new IllegalStateException("Security provider not found");
      }

      return provider;
   }

   public record TestUser(
      String name,
      String email,
      String password,
      String passwordHash) {}
   public record TestOrganization(
      String id,
      String name,
      List<TestUser> users,
      List<String> groups,
      List<String> roles,
      Map<String, List<String>> userRoles,
      Map<String, List<String>> groupUsers) {}
}
