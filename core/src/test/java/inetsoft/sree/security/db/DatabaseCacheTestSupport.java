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
import inetsoft.util.db.DBConnectionPool;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Shared set-up of the database provider cache tests: an in-memory Derby database read through
 * the real cache service, and a configured {@link SecurityEngine} whose chain is the singleton
 * host's chain. The host lookup ({@code DatabaseAuthenticationCacheServiceImpl.getProvider})
 * runs on the service's executor thread, so the engine is configured for real rather than
 * with a thread-local static mock.
 */
public final class DatabaseCacheTestSupport {
   public DatabaseCacheTestSupport() {
      url = "jdbc:derby:memory:dbcache_" + (dbCounter++) + ";create=true";
   }

   /**
    * A provider whose JDBC driver comes from the test class path, so that the instances the
    * security chain creates from authc-chain.json can connect, too.
    */
   public static class SeamProvider extends DatabaseAuthenticationProvider {
      public SeamProvider() {
         setMultiTenantSupplier(() -> false);
         setDriverAvailable(DRIVER::equals);
         setDriverSupplier(c -> (Driver) loader().loadClass(DRIVER).getConstructor().newInstance());
      }
   }

   /**
    * A provider whose cache refresh waits for {@link #gate} while it is set, to hold the thread
    * that refreshes it.
    */
   public static class BlockingProvider extends SeamProvider {
      @Override
      public void clearCache() {
         CountDownLatch latch = gate;

         // not while it is read from the configuration file, before it has a name
         if(latch != null && getProviderName() != null) {
            try {
               latch.await(10, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }

         super.clearCache();
      }

      public static volatile CountDownLatch gate;
   }

   /**
    * A provider whose tear-down signals {@link #inTearDown} and then takes {@link #sleepMs}
    * (800 ms by default) while the latch is set, to hold a chain reload between clearing the provider list and setting the
    * new one.
    */
   public static class SlowTearDownProvider extends SeamProvider {
      @Override
      public void tearDown() {
         CountDownLatch latch = inTearDown;

         if(latch != null && getProviderName() != null) {
            latch.countDown();

            try {
               Thread.sleep(sleepMs);
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }

         super.tearDown();
      }

      public static volatile CountDownLatch inTearDown;
      public static volatile long sleepMs = 800L;
   }

   /**
    * Called by the slow query: sleeps once when {@link #slowLoad} is armed.
    */
   public static int sleepMs(int ms) {
      if(slowLoad) {
         slowLoad = false;

         try {
            Thread.sleep(ms);
         }
         catch(InterruptedException ignore) {
            Thread.currentThread().interrupt();
         }
      }

      return 0;
   }

   /**
    * Saves the cache properties, enables the cache and creates the database.
    */
   public void setUp(String interval) throws Exception {
      for(String name : PROPERTIES) {
         saved.put(name, SreeEnv.getProperty(name));
      }

      SreeEnv.setProperty("security.cache", "true");
      SreeEnv.setProperty("security.cache.interval", interval);
      slowLoad = false;
      exec("CREATE TABLE U (NAME VARCHAR(50) NOT NULL, PW VARCHAR(50))",
           "CREATE TABLE UR (USER_NAME VARCHAR(50), ROLE_NAME VARCHAR(50))",
           "CREATE TABLE UE (USER_NAME VARCHAR(50), EMAIL VARCHAR(80))",
           "CREATE TABLE G (GROUP_NAME VARCHAR(50))",
           "CREATE TABLE GU (GROUP_NAME VARCHAR(50), USER_NAME VARCHAR(50))",
           "INSERT INTO U VALUES ('bob', 'pw-bob'), ('alice', 'pw-alice')",
           "INSERT INTO UR VALUES ('bob', 'r-bob'), ('alice', 'r-alice')",
           "INSERT INTO UE VALUES ('bob', 'bob@x'), ('alice', 'alice@z')",
           "INSERT INTO G VALUES ('sales')",
           "INSERT INTO GU VALUES ('sales', 'alice')",
           "CREATE FUNCTION SLEEPMS(MS INT) RETURNS INT PARAMETER STYLE JAVA NO SQL " +
              "LANGUAGE JAVA EXTERNAL NAME '" + DatabaseCacheTestSupport.class.getName() +
              ".sleepMs'");
   }

   /**
    * Tears down the engine's providers and the providers made here, undeploys their cache
    * services and restores the properties.
    */
   public void tearDown() {
      slowLoad = false;

      try {
         SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();

         // also stops the chain listening to authc-chain.json, so that the next test's writes
         // don't reload it while the next engine init tears it down
         if(root != null && root.getAuthenticationProvider() instanceof AuthenticationChain chain) {
            chain.tearDown();
         }
      }
      catch(Exception ignore) {
      }

      for(DatabaseAuthenticationProvider p : created) {
         try {
            p.tearDown();
         }
         catch(Exception ignore) {
         }
      }

      created.clear();

      // stop the services' reload chains before the context closes
      for(String name : names) {
         try {
            Cluster.getInstance().undeploySingletonService("DatabaseSecurity:" + name);
         }
         catch(Exception ignore) {
         }
      }

      names.clear();

      for(Map.Entry<String, String> e : saved.entrySet()) {
         if(e.getValue() == null) {
            SreeEnv.remove(e.getKey());
         }
         else {
            SreeEnv.setProperty(e.getKey(), e.getValue());
         }
      }
   }

   /**
    * Writes authc-chain.json with the providers and initializes the security engine from it.
    *
    * @return the engine's authentication chain (the singleton host's chain).
    */
   public AuthenticationChain initEngine(List<AuthenticationProvider> providers) throws Exception {
      AuthenticationChain writer = new AuthenticationChain();
      writer.setProviders(providers);
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      // these stand for another node; they must not keep listening to the configuration files
      writer.tearDown();
      authzChain.tearDown();
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();
      return (AuthenticationChain)
         SecurityEngine.getSecurity().getSecurityProvider().getAuthenticationProvider();
   }

   /**
    * Creates a provider over the test database.
    */
   public DatabaseAuthenticationProvider provider(String name, String userListQuery) {
      return provider(name, userListQuery, p -> { });
   }

   /**
    * Creates a provider over the test database.
    *
    * @param configure changes the configuration before the provider is named, as the EM does:
    *                  a named provider refreshes its cache on every setter.
    */
   public DatabaseAuthenticationProvider provider(String name, String userListQuery,
                                                  Consumer<DatabaseAuthenticationProvider> configure)
   {
      return provider(new SeamProvider(), name, userListQuery, configure);
   }

   /**
    * Creates a {@link BlockingProvider} over the test database.
    */
   public DatabaseAuthenticationProvider blockingProvider(String name, String userListQuery) {
      return provider(new BlockingProvider(), name, userListQuery, p -> { });
   }

   /**
    * Creates a {@link SlowTearDownProvider} over the test database.
    */
   public DatabaseAuthenticationProvider slowTearDownProvider(String name, String userListQuery) {
      return provider(new SlowTearDownProvider(), name, userListQuery, p -> { });
   }

   private DatabaseAuthenticationProvider provider(DatabaseAuthenticationProvider p, String name,
                                                   String userListQuery,
                                                   Consumer<DatabaseAuthenticationProvider> configure)
   {
      p.setDriver(DRIVER);
      p.setUrl(url);
      p.setRequiresLogin(false);
      p.setHashAlgorithm("None");
      p.setUserQuery("SELECT NAME, PW FROM U WHERE NAME = ?");
      p.setUserListQuery(userListQuery);
      p.setUserRolesQuery("SELECT ROLE_NAME FROM UR WHERE USER_NAME = ?");
      p.setRoleListQuery("SELECT DISTINCT ROLE_NAME FROM UR");
      p.setUserEmailsQuery("SELECT EMAIL FROM UE WHERE USER_NAME = ?");
      p.setGroupListQuery("SELECT GROUP_NAME FROM G");
      p.setGroupUsersQuery("SELECT USER_NAME FROM GU WHERE GROUP_NAME = ?");
      p.setSystemAdministratorRoles(new String[0]);
      configure.accept(p);
      p.setProviderName(name);
      created.add(p);
      names.add(name);
      return p;
   }

   public String getUrl() {
      return url;
   }

   public void exec(String... sql) throws Exception {
      try(Connection connection = connect(); Statement statement = connection.createStatement()) {
         for(String query : sql) {
            statement.execute(query);
         }
      }
   }

   public static DatabaseAuthenticationProvider find(AuthenticationChain chain, String name) {
      return chain.getProviders().stream()
         .filter(p -> name.equals(p.getProviderName()))
         .map(DatabaseAuthenticationProvider.class::cast)
         .findFirst()
         .orElse(null);
   }

   /**
    * @return the users in the replicated cache map of the provider name.
    */
   public static Set<?> cachedUsers(String name) {
      Map<String, Set<?>> lists =
         Cluster.getInstance().getReplicatedMap("DatabaseSecurity:" + name + ".lists");
      Set<?> users = lists.get("users");
      return users == null ? Set.of() : users;
   }

   public static boolean cachedAlice(String name) {
      return cachedUsers(name).contains(ALICE);
   }

   public static long clusterLong(String name, String key) {
      return Cluster.getInstance().getLong("DatabaseSecurity:" + name + "." + key).get();
   }

   public static void setClusterLong(String name, String key, long value) {
      Cluster.getInstance().getLong("DatabaseSecurity:" + name + "." + key).set(value);
   }

   public static boolean await(BooleanSupplier condition, long timeoutMs)
      throws InterruptedException
   {
      long end = System.currentTimeMillis() + timeoutMs;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            return false;
         }

         Thread.sleep(20);
      }

      return true;
   }

   private Connection connect() throws Exception {
      Driver d = (Driver) loader().loadClass(DRIVER).getConstructor().newInstance();
      return d.connect(url, new Properties());
   }

   private static ClassLoader loader() {
      ClassLoader l = Thread.currentThread().getContextClassLoader();
      return l == null ? DBConnectionPool.class.getClassLoader() : l;
   }

   public static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
   public static final IdentityID ALICE =
      new IdentityID("alice", Organization.getDefaultOrganizationID());
   public static final String ALL_USERS = "SELECT NAME FROM U";
   public static final String NO_ALICE = "SELECT NAME FROM U WHERE NAME <> 'alice'";
   public static final String NO_ALICE_SLOW = NO_ALICE + " AND SLEEPMS(1500) = 0";
   public static volatile boolean slowLoad;

   private static final String[] PROPERTIES = {
      "security.cache", "security.cache.interval", "security.enabled"
   };
   private static int dbCounter = 0;
   private final String url;
   private final Map<String, String> saved = new HashMap<>();
   private final List<DatabaseAuthenticationProvider> created = new ArrayList<>();
   private final Set<String> names = new HashSet<>();
}
