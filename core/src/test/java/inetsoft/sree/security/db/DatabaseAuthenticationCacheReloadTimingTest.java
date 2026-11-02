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
import inetsoft.sree.security.AuthenticationChain;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static inetsoft.sree.security.db.DatabaseAuthenticationCacheReloadTest.CountingService;
import static inetsoft.sree.security.db.DatabaseAuthenticationCacheReloadTest.invoke;
import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78796: the periodic reload chain must survive whatever a tick returns and never spin.
 * These tests count reload ticks over a few seconds each.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DatabaseAuthenticationCacheReloadTimingTest {
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
   void overdueWithoutClientsWaitsTheIdleDelay() throws Exception {
      SreeEnv.setProperty("security.cache.interval", "600000");
      String name = "idle78796";
      setClusterLong(name, "lastLoadTime", System.currentTimeMillis() - 700_000L);
      setClusterLong(name, "clientCount", 0L);
      CountingService service = deploy(name);

      Thread.sleep(3_000);

      assertEquals(1, service.ticks.get(), "one tick, then the idle delay");
   }

   @Test
   void overdueWhileAnotherLoadHoldsTheLockTicksAtMostOncePerSecond() throws Exception {
      SreeEnv.setProperty("security.cache.interval", "600000");
      String name = "held78796";
      long loaded = System.currentTimeMillis() - 700_000L;
      setClusterLong(name, "lastLoadTime", loaded);
      setClusterLong(name, "clientCount", 1L);
      CountingService service = deploy(name);
      // after the deployment, which clears a lock left by a previous host
      setClusterLong(name, "loadingCount", 1L);

      Thread.sleep(3_200);

      int ticks = service.ticks.get();
      assertTrue(ticks >= 2 && ticks <= 4, "ticks in 3.2 s: " + ticks);
      assertEquals(loaded, clusterLong(name, "lastLoadTime"));
      assertEquals(0, service.lookups.get(), "a tick that lost the lock loaded nothing");
      setClusterLong(name, "loadingCount", 0L);
   }

   @Test
   void failureJustAfterASuccessIsRetriedAfterAMinute() throws Exception {
      String name = "failed78796";
      long now = System.currentTimeMillis();
      setClusterLong(name, "lastLoadTime", now - 1_000L);
      setClusterLong(name, "lastFailureTime", now - 500L);
      setClusterLong(name, "clientCount", 1L);
      CountingService service = deploy(name);

      long delay = (Long) invoke(service, "getNextReloadDelay");
      assertTrue(delay > 55_000L && delay <= 60_000L, "failure delay " + delay);
      assertFalse((Boolean) invoke(service, "isReloadRequired"));

      Thread.sleep(3_000);

      assertEquals(0, service.ticks.get(), "no tick before the failure delay");
   }

   @Test
   void cacheTurnedOffAtRuntimeDoesNotReload() throws Exception {
      SreeEnv.setProperty("security.cache.interval", "600000");
      String name = "off78796";
      long loaded = System.currentTimeMillis() - 700_000L;
      setClusterLong(name, "lastLoadTime", loaded);
      setClusterLong(name, "clientCount", 1L);
      CountingService service = deploy(name);
      SreeEnv.setProperty("security.cache", "false");

      Thread.sleep(3_000);

      assertEquals(1, service.ticks.get());
      assertEquals(loaded, clusterLong(name, "lastLoadTime"));
      assertEquals(3_600_000L, (Long) invoke(service, "getNextReloadDelay"));
      assertFalse((Boolean) invoke(service, "isReloadRequired"));
   }

   @Test
   void chainSurvivesManualRefreshAndAHeldLoadLock() throws Exception {
      AuthenticationChain chain = support.initEngine(List.of(support.provider("reload78796b", ALL_USERS)));
      DatabaseAuthenticationProvider p = find(chain, "reload78796b");
      p.getUsers();
      assertTrue(await(p::isCacheInitialized, 5_000));

      // a manual refresh in the middle of an interval used to be one of the early returns
      p.clearCache();
      Thread.sleep(500);

      // another load holds the lock across at least one tick
      setClusterLong("reload78796b", "loadingCount", 1L);
      Thread.sleep(2_000);
      setClusterLong("reload78796b", "loadingCount", 0L);

      support.exec("INSERT INTO U VALUES ('dave', 'pw-dave')");
      assertTrue(await(() -> cachedUsers("reload78796b").toString().contains("dave"), 8_000),
                 "the reload chain did not survive");
   }

   private CountingService deploy(String name) {
      CountingService service = (CountingService) Cluster.getInstance().getSingletonService(
         "DatabaseSecurity:" + name, DatabaseAuthenticationCacheService.class,
         () -> new CountingService(name));
      services.add(service);
      return service;
   }

   private DatabaseCacheTestSupport support;
   private final List<DatabaseAuthenticationCacheServiceImpl> services = new ArrayList<>();
}
