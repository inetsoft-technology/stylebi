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
import inetsoft.sree.security.*;
import inetsoft.test.SreeHome;
import inetsoft.util.db.DBConnectionPool;
import org.apache.commons.io.IOUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

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

import static org.junit.jupiter.api.Assertions.*;

@SreeHome
@Tag("slow")
class DatabaseAuthenticationProviderTests {
   private DatabaseAuthenticationProvider provider;

   private static int testCounter = 1;
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static final String URL = "jdbc:derby:memory:authtest;create=true";

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

   @Test
   void getOrganizationIdsShouldReturnCorrectValues() {
      waitForCache();
      String[] expected = { "host-org" };
      String[] actual = provider.getOrganizationIDs();
      assertNotNull(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   void getOrganizationNameShouldReturnCorrectValue() {
      waitForCache();
      String expected = "Host Organization";
      String actual = provider.getOrganizationName("host-org");
      assertEquals(expected, actual);
   }

   @Test
   void getRolesShouldReturnCorrectValues() {
      waitForCache();
      IdentityID[] expected = {
         new IdentityID("Org Admin", null),
         new IdentityID("Site Admin", null)
      };
      IdentityID[] actual = provider.getRoles();
      assertNotNull(actual);
      Arrays.sort(expected);
      assertArrayEquals(expected, actual);
   }

   @Test
   void getUsersShouldReturnCorrectValues() {
      waitForCache();
      IdentityID[] expected = { new IdentityID("admin", "host-org") };
      IdentityID[] actual = provider.getUsers();
      assertNotNull(actual);
      assertArrayEquals(expected, actual);
   }

   @Test
   void getUserRoleShouldReturnCorrectValue() {
      waitForCache();
      IdentityID[] expected = { new IdentityID("Site Admin", null) };
      IdentityID[] actual = provider.getRoles(new IdentityID("admin", "host-org"));
      assertNotNull(actual);
      assertArrayEquals(expected, actual);
   }

   private void waitForCache() {
      provider.getCache(true);
      Awaitility.await()
         .atMost(Duration.ofMinutes(1L))
         .pollInterval(Duration.ofMillis(500L))
         .until(provider::isCacheInitialized);
   }

   private void createProvider() throws Exception {
      provider = new DatabaseAuthenticationProvider();
      provider.setDriver(DRIVER);
      provider.setUrl(URL);
      provider.setRequiresLogin(false);
      provider.setUserQuery("SELECT USER_NAME, PW_HASH, PW_SALT FROM INETSOFT_USER WHERE ORG_ID=? AND USER_NAME=?");
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
}
