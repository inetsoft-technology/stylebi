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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.AuthenticationChain;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78796: the cluster cache service of a Database provider must reload at the cache
 * interval without a manual clear-cache, start one reload chain per deployed instance, and
 * survive a failover. The tick timing tests are in {@link DatabaseAuthenticationCacheReloadTimingTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseAuthenticationCacheReloadTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("1500");
   }

   @AfterEach
   void tearDown() {
      for(DatabaseAuthenticationCacheServiceImpl service : services) {
         service.cancel();
      }

      services.clear();
      support.tearDown();
   }

   @Test
   void reloadsAtTheIntervalWithoutClearCache() throws Exception {
      AuthenticationChain chain = support.initEngine(List.of(support.provider("reload78796a", ALL_USERS)));
      DatabaseAuthenticationProvider p = find(chain, "reload78796a");
      p.getUsers();
      assertTrue(await(p::isCacheInitialized, 5_000));
      long first = clusterLong("reload78796a", "lastLoadTime");

      support.exec("INSERT INTO U VALUES ('carol', 'pw-carol')");

      // the host looks the provider up in the real engine chain on the service's own thread
      assertTrue(await(() -> cachedUsers("reload78796a").toString().contains("carol"), 8_000),
                 "a new database user never reached the cache");
      long second = clusterLong("reload78796a", "lastLoadTime");
      assertTrue(second > first);
      assertTrue(await(() -> clusterLong("reload78796a", "lastLoadTime") > second, 5_000),
                 "the reload chain stopped after one reload");
   }

   @Test
   void repeatedConnectsAddNoTimers() throws Exception {
      String name = "connect78796";
      CountingService service = deploy(name);
      service.lookup.set(support.provider(name, ALL_USERS));

      for(int i = 0; i < 5; i++) {
         service.connect();
      }

      assertEquals(2, queueSize(service), "the cleanup task and one reload chain");

      for(int i = 0; i < 5; i++) {
         service.disconnect();
      }
   }

   @Test
   void initAloneStartsNoChain() throws Exception {
      DatabaseAuthenticationCacheServiceImpl service =
         new DatabaseAuthenticationCacheServiceImpl("init78796");
      service.init();
      services.add(service);

      assertEquals(1, queueSize(service), "only the cleanup task");
   }

   @Test
   void redeployedInstanceClearsAStaleLockAndLoadsWithoutABoundProvider() throws Exception {
      String name = "failover78796";
      setClusterLong(name, "loadingCount", 1L);
      setClusterLong(name, "lastLoadTime", System.currentTimeMillis() - 10_000L);
      setClusterLong(name, "clientCount", 1L);
      CountingService service = deploy(name);

      assertEquals(0L, clusterLong(name, "loadingCount"), "the lock of the dead host is cleared");
      assertEquals(2, queueSize(service), "the cleanup task and the reload chain");
      assertNull(boundProvider(service));

      // not in this host's chain and never bound: a failed attempt, not an exception
      service.refresh();
      assertTrue(await(() -> clusterLong(name, "lastFailureTime") != 0L, 5_000));
      assertEquals(0L, clusterLong(name, "loadingCount"));

      DatabaseAuthenticationProvider p = support.provider(name, ALL_USERS);
      service.lookup.set(p);
      service.refresh();
      assertTrue(await(() -> cachedAlice(name), 5_000), "the refresh did not load");
      assertSame(p, boundProvider(service));
   }

   private CountingService deploy(String name) {
      CountingService service = (CountingService) Cluster.getInstance().getSingletonService(
         "DatabaseSecurity:" + name, DatabaseAuthenticationCacheService.class,
         () -> new CountingService(name));
      services.add(service);
      return service;
   }

   private static int queueSize(DatabaseAuthenticationCacheServiceImpl service) throws Exception {
      Field field = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredField("executor");
      field.setAccessible(true);
      return ((ScheduledThreadPoolExecutor) field.get(service)).getQueue().size();
   }

   private static Object boundProvider(DatabaseAuthenticationCacheServiceImpl service)
      throws Exception
   {
      Field field = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredField("provider");
      field.setAccessible(true);
      return field.get(service);
   }

   static Object invoke(DatabaseAuthenticationCacheServiceImpl service, String method)
      throws Exception
   {
      Method m = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredMethod(method);
      m.setAccessible(true);
      return m.invoke(service);
   }

   /**
    * Counts the reload ticks. Its host chain is a reference: empty means that the provider is
    * not in the chain.
    */
   static final class CountingService extends DatabaseAuthenticationCacheServiceImpl {
      CountingService(String providerName) {
         super(providerName);
      }

      @Override
      void scheduledReload() {
         ticks.incrementAndGet();
         super.scheduledReload();
      }

      @Override
      DatabaseAuthenticationProvider lookupProvider() {
         lookups.incrementAndGet();
         DatabaseAuthenticationProvider p = lookup.get();

         if(p == null) {
            throw new IllegalStateException("not in the host chain");
         }

         return p;
      }

      final transient AtomicInteger ticks = new AtomicInteger();
      final transient AtomicInteger lookups = new AtomicInteger();
      final transient AtomicReference<DatabaseAuthenticationProvider> lookup =
         new AtomicReference<>();
   }

   private DatabaseCacheTestSupport support;
   private final List<DatabaseAuthenticationCacheServiceImpl> services = new ArrayList<>();
}
