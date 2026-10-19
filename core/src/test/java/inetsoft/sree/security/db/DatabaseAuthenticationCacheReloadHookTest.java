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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.jdbc.JDBCHandler;
import inetsoft.util.DataSpace;
import inetsoft.web.admin.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78793: a Database provider that is removed and added again under the same name, or
 * changed, must not keep serving the cluster cache of the old configuration. The singleton host
 * refreshes the cache after its own chain has reloaded the configuration file.
 * <p>
 * The engine's chain stands for the singleton host's chain, and a second chain writing
 * authc-chain.json stands for the node where the change is saved. The host lookup runs on the
 * cache service's own thread against the real engine.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DatabaseAuthenticationCacheReloadHookTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      baseName = "base78793_" + info.getTestMethod().orElseThrow().getName();
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
   }

   @AfterEach
   void tearDown() {
      BlockingProvider.gate = null;

      if(writer != null) {
         writer.tearDown();
         writer = null;
      }

      support.tearDown();
   }

   @Test
   void removeAndReAddOnAnotherNodeLoadsTheNewConfiguration() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base(), support.provider("p78793", NO_ALICE)));
      load(host, baseName, "p78793");
      assertFalse(cachedAlice("p78793"));
      writer = new AuthenticationChain();

      // the other node removes the provider ...
      save(base());
      reload(host);
      // ... and adds it again with a user list that includes alice
      save(base(), support.provider("p78793", ALL_USERS));
      reload(host);

      DatabaseAuthenticationProvider added = find(host, "p78793");
      assertTrue(await(() -> Arrays.asList(added.getUsers()).contains(ALICE), 3_000),
                 "the re-added provider serves the removed configuration's users");
      assertEquals(0L, clusterLong(baseName, "refreshSeq"), "the unchanged provider was refreshed");
   }

   @Test
   void changeArrivingWhileAnOldConfigurationLoadRunsIsLoadedAfterIt() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base(), support.provider("q78793", NO_ALICE_SLOW)));
      load(host, "q78793");
      writer = new AuthenticationChain();

      // a refresh of the old configuration is loading when the new configuration arrives
      slowLoad = true;
      find(host, "q78793").clearCache();
      assertTrue(await(() -> clusterLong("q78793", "loadingCount") == 1L, 2_000));
      save(base(), support.provider("q78793", ALL_USERS));
      reload(host);
      assertEquals(1L, clusterLong("q78793", "loadingCount"), "the old load is still running");

      // the hook's refresh lost the load lock; it runs again after the old load
      assertTrue(await(() -> cachedAlice("q78793"), 5_000), "the new configuration was never loaded");
   }

   @Test
   void unrelatedChangeDoesNotRefreshALoginProtectedProvider() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base(), support.provider("r78793", NO_ALICE)));
      DatabaseAuthenticationProvider protectedProvider = find(host, baseName);
      assertTrue(protectedProvider.isRequiresLogin());
      // the credential is encrypted with a random IV, so it differs on every write
      ObjectMapper mapper = new ObjectMapper();
      assertNotEquals(protectedProvider.writeConfiguration(mapper).get("credential"),
                      protectedProvider.writeConfiguration(mapper).get("credential"));
      load(host, baseName, "r78793");
      long loaded = clusterLong(baseName, "lastLoadTime");
      writer = new AuthenticationChain();

      save(base(), support.provider("r78793", ALL_USERS));
      reload(host);

      assertTrue(await(() -> cachedAlice("r78793"), 3_000), "the changed provider was not refreshed");
      Thread.sleep(500);
      assertEquals(0L, clusterLong(baseName, "refreshSeq"));
      assertEquals(loaded, clusterLong(baseName, "lastLoadTime"));
   }

   @Test
   void changedLoginIsRefreshed() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base()));
      load(host, baseName);
      writer = new AuthenticationChain();
      save(base("other"));
      reload(host);

      assertTrue(await(() -> clusterLong(baseName, "refreshSeq") > 0L, 3_000));
   }

   @Test
   void slowRefreshDoesNotHoldTheReloadingThread() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base()));
      // this thread stands for the storage event thread: it is the only one that reloads
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      writer = new AuthenticationChain();
      BlockingProvider.gate = new CountDownLatch(1);

      try {
         save(base(), support.blockingProvider("u78793", ALL_USERS));
         long start = System.currentTimeMillis();
         reload(host);
         long elapsed = System.currentTimeMillis() - start;

         // the storage event thread that runs the reload is free while the refresh waits
         assertTrue(elapsed < 2_000L, "the reload waited " + elapsed + " ms for the refresh");
         assertNotNull(find(host, "u78793"), "the new provider is in the chain before the refresh");
         assertEquals(0L, clusterLong("u78793", "refreshSeq"));
      }
      finally {
         BlockingProvider.gate.countDown();
      }

      assertTrue(await(() -> cachedAlice("u78793"), 5_000), "the refresh never ran");
      BlockingProvider.gate = null;
   }

   @Test
   void queuedRefreshOfAReplacedProviderIsNotLost() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(
         base(), support.blockingProvider("v78793", NO_ALICE), support.provider("w78793", NO_ALICE)));
      DataSpace.getDataSpace().removeChangeListener(null, "authc-chain.json", host);
      load(host, "w78793");
      assertFalse(cachedAlice("w78793"));
      writer = new AuthenticationChain();
      BlockingProvider.gate = new CountDownLatch(1);

      try {
         // reload 1 changes v and w; v's refresh holds the refresh thread, so w's waits behind it
         save(base(), support.blockingProvider("v78793", ALL_USERS),
              support.provider("w78793", ALL_USERS));
         reload(host);
         // reload 2 changes only v, and replaces every instance, w's too
         save(base(), support.blockingProvider("v78793", NO_ALICE),
              support.provider("w78793", ALL_USERS));
         reload(host);
         assertFalse(cachedAlice("w78793"));
      }
      finally {
         BlockingProvider.gate.countDown();
      }

      // w's queued refresh runs on w's current instance
      assertTrue(await(() -> cachedAlice("w78793"), 5_000), "w's refresh was lost");
      BlockingProvider.gate = null;
   }

   @Test
   void initialLoadRefreshesNothing() throws Exception {
      support.initEngine(List.of(base(), support.provider("s78793", ALL_USERS)));
      Thread.sleep(500);

      assertEquals(0L, clusterLong("s78793", "refreshSeq"));
      assertEquals(0L, clusterLong("s78793", "clientCount"), "nothing connected to the cache");
      assertEquals(0L, clusterLong("s78793", "lastLoadTime"));
   }

   @Test
   void addOnTheHostNodeRefreshesAReusedCache() throws Exception {
      AuthenticationChain host = support.initEngine(List.of(base(), support.provider("t78793", NO_ALICE)));
      load(host, "t78793");

      // removed on this node, as AuthenticationProviderService.removeAuthenticationProvider does:
      // the cluster state of the name stays for the cleanup grace period
      List<AuthenticationProvider> list = host.getProviders();
      AuthenticationProvider removed = find(host, "t78793");
      list.remove(removed);
      removed.tearDown();
      host.setProviders(list);
      long loaded = clusterLong("t78793", "lastLoadTime");

      AuthenticationProviderService service = new AuthenticationProviderService(
         SecurityEngine.getSecurity(), new ObjectMapper(), mock(SimpMessagingTemplate.class),
         Cluster.getInstance());
      AuthenticationProviderModel model = AuthenticationProviderModel.builder()
         .providerName("t78793")
         .providerType(SecurityProviderType.DATABASE)
         .dbProviderModel(DatabaseAuthenticationProviderModel.builder()
                             .driver(DRIVER)
                             .url(support.getUrl())
                             .requiresLogin(false)
                             .hashAlgorithm("None")
                             .userQuery("SELECT NAME, PW FROM U WHERE NAME = ?")
                             .userListQuery(ALL_USERS)
                             .build())
         .build();

      // the provider built from the model tests its connection through the plugin drivers
      try(MockedStatic<JDBCHandler> jdbc = mockStatic(JDBCHandler.class, CALLS_REAL_METHODS)) {
         jdbc.when(() -> JDBCHandler.isDriverAvailable(anyString())).thenReturn(true);
         jdbc.when(() -> JDBCHandler.getDriver(anyString())).thenAnswer(inv -> derbyDriver());
         service.addAuthenticationProvider(model, "t78793", null);
      }

      assertTrue(await(() -> clusterLong("t78793", "refreshSeq") > 0L, 3_000),
                 "the add did not refresh the reused cache");
      // the refresh ran on the host: it loaded, or failed on the provider's plugin driver,
      // which the cache thread can't see
      assertTrue(await(() -> clusterLong("t78793", "lastLoadTime") != loaded ||
                          clusterLong("t78793", "lastFailureTime") > loaded, 3_000));
   }

   /**
    * @return a login-protected provider, named after the test so that no cluster state is
    *         shared between tests.
    */
   private DatabaseAuthenticationProvider base() {
      return base("app");
   }

   private DatabaseAuthenticationProvider base(String user) {
      return support.provider(baseName, ALL_USERS, p -> {
         p.setRequiresLogin(true);
         p.setDbUser(user);
         p.setDbPassword("secret");
      });
   }

   private void save(AuthenticationProvider... providers) throws Exception {
      // DataSpace timestamps have millisecond resolution
      Thread.sleep(20);
      writer.setProviders(new ArrayList<>(List.of(providers)));
   }

   private static void reload(AuthenticationChain chain) throws Exception {
      Method method = SecurityChain.class.getDeclaredMethod("loadConfiguration");
      method.setAccessible(true);
      method.invoke(chain);
   }

   private static void load(AuthenticationChain chain, String... names) throws Exception {
      for(String name : names) {
         DatabaseAuthenticationProvider p = find(chain, name);
         p.getUsers();
         assertTrue(await(p::isCacheInitialized, 5_000), name + " never loaded");
      }
   }

   private static Driver derbyDriver() throws Exception {
      return (Driver) Class.forName(DRIVER).getConstructor().newInstance();
   }

   private DatabaseCacheTestSupport support;
   private AuthenticationChain writer;
   private String baseName;
}
