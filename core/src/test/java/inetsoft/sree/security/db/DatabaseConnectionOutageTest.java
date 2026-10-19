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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Files;
import java.security.Principal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78800: once the database of a database security provider becomes unreachable, a lookup
 * must not wait for the connection pool timeout on every call, a user of another provider must
 * not reach the database provider at all for its emails, and the provider must recover shortly
 * after the database comes back.
 * <p>
 * Chain [File, Database], cache off, in-memory Derby behind a driver that can be switched to
 * fail (or hang) like an unreachable host. The Hikari connection timeout is 2 s (via
 * {@code hikaricp.configurationFile}), so the pool timeout and the connection probe are 2 s each.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DatabaseConnectionOutageTest {
   @BeforeAll
   static void configureHikari() throws Exception {
      hikariProperties = File.createTempFile("hikari78800", ".properties");
      Files.writeString(
         hikariProperties.toPath(),
         "connectionTimeout=" + POOL_TIMEOUT + "\nvalidationTimeout=1000\nmaximumPoolSize=2\n");
      System.setProperty("hikaricp.configurationFile", hikariProperties.getAbsolutePath());
   }

   @AfterAll
   static void restoreHikari() {
      System.clearProperty("hikaricp.configurationFile");

      if(hikariProperties != null) {
         hikariProperties.delete();
      }
   }

   @BeforeEach
   void savePrincipal() {
      principal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void tearDown() throws Exception {
      hangMillis = 0;
      down = false;

      if(fileProvider != null) {
         // the file provider's users are stored, so they would leak into the next test
         fileProvider.removeUser(fileUser);
         fileProvider.removeUser(dbUser);
         fileProvider = null;
      }

      if(dbProvider != null) {
         dbProvider.tearDown();
         dbProvider = null;
      }

      // let a timed out probe finish before the context closes
      long end = System.currentTimeMillis() + 15000;

      while(countProbeThreadsConnecting() > 0 && System.currentTimeMillis() < end) {
         Thread.sleep(100);
      }

      ThreadContext.setContextPrincipal(principal);
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
      SreeEnv.remove("security.cache");
      SreeEnv.remove("database.security.connection.poolFailureRetryInterval");
   }

   @Test
   void dbDownAfterPoolCreated_onlyFirstLookupWaitsForPoolTimeout() throws Exception {
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();

      long first = time(() -> engine.getEmails(dbUser));
      assertTrue(first >= POOL_TIMEOUT - 200, "first lookup waits for the pool: " + first);

      for(int i = 0; i < 3; i++) {
         long next = time(() -> engine.getEmails(dbUser));
         assertTrue(next < 500, "later lookups fail fast: " + next);
      }

      long login = time(() -> chain().authenticate(dbUser, new DefaultTicket(dbUser, "pw")));
      assertTrue(login < 500, "authenticate fails fast: " + login);
   }

   @Test
   void dbDown_fileUserEmailsDoNotReachDatabaseProvider() throws Exception {
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();

      for(int i = 0; i < 3; i++) {
         long[] ms = new long[1];
         String[] emails = timed(ms, () -> engine.getEmails(fileUser));
         assertArrayEquals(new String[] { "file@x" }, emails);
         assertTrue(ms[0] < 500, "file user's emails don't wait for the database: " + ms[0]);
      }
   }

   @Test
   void dbProviderFirst_dbDown_onlyFirstLookupWaits() throws Exception {
      setUp(true);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();

      // the database provider is asked first and can't answer: one pool timeout, then fast
      assertArrayEquals(new String[] { "file@x" }, engine.getEmails(fileUser));

      for(int i = 0; i < 3; i++) {
         long[] ms = new long[1];
         String[] emails = timed(ms, () -> engine.getEmails(fileUser));
         assertArrayEquals(new String[] { "file@x" }, emails);
         assertTrue(ms[0] < 500, "later lookups fail fast: " + ms[0]);
      }
   }

   @Test
   void concurrentLookups_doNotQueueBehindEachOthersPoolTimeout() throws Exception {
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();
      ExecutorService pool = Executors.newFixedThreadPool(3);

      try {
         List<Future<Long>> futures = new ArrayList<>();

         for(int i = 0; i < 3; i++) {
            futures.add(pool.submit(() -> time(() -> engine.getEmails(dbUser))));
         }

         for(Future<Long> future : futures) {
            long ms = future.get(60, TimeUnit.SECONDS);
            // before the fix: 2, 4 and 6 s (each waits for the pool behind the lock)
            assertTrue(ms < POOL_TIMEOUT + 1500, "concurrent lookup: " + ms);
         }
      }
      finally {
         pool.shutdownNow();
         assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
      }
   }

   @Test
   void poolExhaustedWithHealthyDatabase_isNotRememberedAsFailure() throws Exception {
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      ConnectionProvider connectionProvider = dbProvider.getConnectionProvider();
      Connection c1 = connectionProvider.getConnection();
      Connection c2 = connectionProvider.getConnection();

      try {
         // pool exhausted: this lookup times out, but the probe finds the database reachable
         assertArrayEquals(new String[0], engine.getEmails(dbUser));
      }
      finally {
         c1.close();
         c2.close();
      }

      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      assertTrue(chain().authenticate(dbUser, new DefaultTicket(dbUser, "pw")));
   }

   @Test
   void dbBackAfterOutage_recoversAfterShortWindow() throws Exception {
      SreeEnv.setProperty("database.security.connection.poolFailureRetryInterval", "1500");
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();
      engine.getEmails(dbUser);
      bringUp();

      assertTrue(waitForLogin(10000), "database users can log in shortly after the outage");
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
   }

   @Test
   void dbBackAfterOutageAndConnectionReset_recoversAfterShortWindow() throws Exception {
      SreeEnv.setProperty("database.security.connection.poolFailureRetryInterval", "1500");
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();
      // what the cache loader does when a load fails (DatabaseAuthenticationCacheServiceImpl
      // handleError): the pool is closed and the next lookup tests the connection from scratch
      dbProvider.getConnectionProvider().resetConnection();
      assertArrayEquals(new String[0], engine.getEmails(dbUser));
      long next = time(() -> engine.getEmails(dbUser));
      assertTrue(next < 500, "the failed connection test is remembered: " + next);
      bringUp();

      assertTrue(waitForLogin(10000), "database users can log in shortly after the outage");
   }

   @Test
   void dbNeverReachable_keepsLongRetryInterval() throws Exception {
      SreeEnv.setProperty("database.security.connection.poolFailureRetryInterval", "1500");
      setUp(false);
      down = true;
      dbProvider.getConnectionProvider().resetConnection();
      assertArrayEquals(new String[0], engine.getEmails(dbUser));
      bringUp();
      Thread.sleep(2500);

      // a configuration that never connected is re-tested after the (10 min) retry interval,
      // as before, not after the short outage window
      long[] ms = new long[1];
      assertArrayEquals(new String[0], timed(ms, () -> engine.getEmails(dbUser)));
      assertTrue(ms[0] < 500, "fails fast: " + ms[0]);
   }

   @Test
   void unreachableHost_connectionProbeIsBounded() throws Exception {
      setUp(false);
      assertArrayEquals(new String[] { "db@x" }, engine.getEmails(dbUser));
      takeDown();
      hangMillis = 6000;

      // pool timeout, then the probe gives up after the pool timeout too
      long first = time(() -> engine.getEmails(dbUser));
      assertTrue(first < 2 * POOL_TIMEOUT + 1500, "first lookup is bounded: " + first);
      long next = time(() -> engine.getEmails(dbUser));
      assertTrue(next < 500, "later lookups fail fast: " + next);

      // after a reset, the connection test from scratch is bounded as well
      dbProvider.getConnectionProvider().resetConnection();
      long cold = time(() -> engine.getEmails(dbUser));
      assertTrue(cold < POOL_TIMEOUT + 1500, "connection test is bounded: " + cold);
   }

   @Test
   void userInSeveralProviders_getsEmailsOfFirstOwner() throws Exception {
      setUp(false);
      FSUser user = new FSUser(dbUser);
      user.setEmails(new String[] { "dbuser-file@x" });
      user.setActive(true);
      fileProvider.addUser(user);

      // the same rule as SUtil.getEmails: the first provider that has the user
      assertArrayEquals(new String[] { "dbuser-file@x" }, engine.getEmails(dbUser));
      assertArrayEquals(new String[] { "dbuser-file@x" }, SUtil.getEmails(dbUser));
      assertArrayEquals(new String[0],
                        engine.getEmails(new IdentityID("nobody78800", dbUser.orgID)));
   }

   private void setUp(boolean dbFirst) throws Exception {
      dbName = "outage78800_" + (counter++);
      url = "jdbc:derby:memory:" + dbName + ";create=true";
      createTables();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "false");
      SreeEnv.setProperty("security.cache", "false");
      SreeEnv.save();

      FileAuthenticationProvider file = new FileAuthenticationProvider();
      file.setProviderName("Primary");
      DatabaseAuthenticationProvider db = new DatabaseAuthenticationProvider();
      db.setProviderName("DB78800_" + dbName);
      db.setDriver(DRIVER);
      db.setUrl(url);
      db.setRequiresLogin(false);
      db.setHashAlgorithm("None");
      db.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      db.setUserListQuery("SELECT NAME FROM U");
      db.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
      db.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
      db.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
      db.setGroupListQuery("SELECT GROUP_NAME FROM G");
      db.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");

      AuthenticationChain authc = new AuthenticationChain();
      authc.setProviders(dbFirst ? List.of(db, file) : List.of(file, db));
      authc.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      engine = SecurityEngine.getSecurity();
      engine.init();
      List<AuthenticationProvider> providers = chain().getProviders();
      fileProvider = (FileAuthenticationProvider) providers.get(dbFirst ? 1 : 0);
      dbProvider = (DatabaseAuthenticationProvider) providers.get(dbFirst ? 0 : 1);
      dbProvider.setMultiTenantSupplier(() -> false);
      dbProvider.setDriverAvailable(DRIVER::equals);
      dbProvider.setDriverSupplier(c -> new SwitchableDriver());
      dbProvider.resetConnection();
      assertFalse(dbProvider.isCacheEnabled());

      FSUser user = new FSUser(fileUser);
      user.setEmails(new String[] { "file@x" });
      user.setRoles(new IdentityID[] { new IdentityID("Everyone", fileUser.orgID) });
      user.setActive(true);
      fileProvider.addUser(user);
   }

   private AuthenticationChain chain() {
      return (AuthenticationChain) engine.getSecurityProvider().getAuthenticationProvider();
   }

   private void takeDown() throws Exception {
      down = true;

      try {
         // close the pooled connections, as a database restart does
         DriverManager.getConnection("jdbc:derby:memory:" + dbName + ";shutdown=true");
      }
      catch(SQLException expected) {
         // shutdown always throws
      }

      // past Hikari's 500 ms window in which a pooled connection isn't validated
      Thread.sleep(1200);
   }

   private void bringUp() throws Exception {
      down = false;
      createTables();
   }

   private boolean waitForLogin(long timeout) throws Exception {
      long end = System.currentTimeMillis() + timeout;

      while(System.currentTimeMillis() < end) {
         if(chain().authenticate(dbUser, new DefaultTicket(dbUser, "pw"))) {
            return true;
         }

         Thread.sleep(200);
      }

      return false;
   }

   private void createTables() throws Exception {
      String[] sql = {
         "CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
         "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
         "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
         "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
         "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
         "INSERT INTO U VALUES ('dbuser78800', 'pw')",
         "INSERT INTO UR VALUES ('dbuser78800', 'r-db')",
         "INSERT INTO UE VALUES ('dbuser78800', 'db@x')",
         "INSERT INTO G VALUES ('g-db')",
         "INSERT INTO GU VALUES ('g-db', 'dbuser78800')"
      };

      try(Connection connection = DriverManager.getConnection(url);
          Statement statement = connection.createStatement())
      {
         // an in-memory database keeps its content across a shutdown, so after the first
         // creation every statement here fails with "already exists"
         try {
            statement.execute(sql[0]);
         }
         catch(SQLException e) {
            return;
         }

         for(int i = 1; i < sql.length; i++) {
            statement.execute(sql[i]);
         }
      }
   }

   private static int countProbeThreadsConnecting() {
      int count = 0;

      for(Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
         if(e.getKey().getName().startsWith("DatabaseSecurityConnectionProbe")) {
            for(StackTraceElement frame : e.getValue()) {
               if(frame.getClassName().endsWith("SwitchableDriver")) {
                  count++;
                  break;
               }
            }
         }
      }

      return count;
   }

   private static long time(Runnable call) {
      long start = System.nanoTime();
      call.run();
      return (System.nanoTime() - start) / 1_000_000;
   }

   private static <T> T timed(long[] ms, Supplier<T> call) {
      long start = System.nanoTime();
      T result = call.get();
      ms[0] = (System.nanoTime() - start) / 1_000_000;
      return result;
   }

   private class SwitchableDriver implements Driver {
      @Override
      public Connection connect(String url, Properties info) throws SQLException {
         long hang = hangMillis;

         if(hang > 0) {
            // like a black-holed host: ignores interrupts
            long end = System.currentTimeMillis() + hang;

            while(System.currentTimeMillis() < end) {
               try {
                  Thread.sleep(100);
               }
               catch(InterruptedException ignore) {
               }
            }

            throw new SQLException("simulated: connect timed out", "08001");
         }

         if(down) {
            throw new SQLException("simulated: database unreachable", "08001");
         }

         return DriverManager.getConnection(url, info);
      }

      @Override
      public boolean acceptsURL(String url) {
         return url.startsWith("jdbc:derby:");
      }

      @Override
      public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
         return new DriverPropertyInfo[0];
      }

      @Override
      public int getMajorVersion() {
         return 1;
      }

      @Override
      public int getMinorVersion() {
         return 0;
      }

      @Override
      public boolean jdbcCompliant() {
         return false;
      }

      @Override
      public Logger getParentLogger() {
         return Logger.getGlobal();
      }
   }

   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   private static final long POOL_TIMEOUT = 2000L;
   private static File hikariProperties;
   private static int counter = 0;

   private String dbName;
   private String url;
   private volatile boolean down;
   private volatile long hangMillis;
   private SecurityEngine engine;
   private FileAuthenticationProvider fileProvider;
   private DatabaseAuthenticationProvider dbProvider;
   private Principal principal;
   private final IdentityID fileUser =
      new IdentityID("fileuser78800", Organization.getDefaultOrganizationID());
   private final IdentityID dbUser =
      new IdentityID("dbuser78800", Organization.getDefaultOrganizationID());
}
