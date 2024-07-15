/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.DistributedScheduledExecutorService;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class IgniteScheduledExecutorServiceTest {
   @TempDir
   static Path clusterDir;

   @BeforeAll
   static void setup() {
      TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);
      ignite1 = IgniteClusterTestUtils.getIgniteCluster("ignite1", ipFinder, clusterDir);
      ignite2 = IgniteClusterTestUtils.getIgniteCluster("ignite2", ipFinder, clusterDir);
   }

   @AfterAll
   static void destroy() {
      ignite1.close();
      ignite1 = null;
      ignite2.close();
      ignite2 = null;
   }

   @Test
   void executesScheduledCommands() throws InterruptedException, ExecutionException {
      DistributedMap<String, Boolean> cache = ignite1.getMap("cache");
      DistributedScheduledExecutorService executorService = ignite2.getScheduledExecutor();
      executorService.scheduleAtFixedRate(new TestRunnableTask("scheduleAtFixedRate"),
                                          0, 1, TimeUnit.SECONDS);
      executorService.schedule(new TestRunnableTask("scheduleRunnable"), 1, TimeUnit.SECONDS);
      executorService.schedule(new TestCallableTask("scheduleCallable"), 1, TimeUnit.SECONDS);

      Thread.sleep(3000);

      assertNotNull(cache.get("scheduleAtFixedRate"));
      assertNotNull(cache.get("scheduleRunnable"));
      assertNotNull(cache.get("scheduleCallable"));
      assertTrue(cache.get("scheduleAtFixedRate"));
      assertTrue(cache.get("scheduleRunnable"));
      assertTrue(cache.get("scheduleCallable"));
   }

   private static IgniteCluster ignite1;
   private static IgniteCluster ignite2;

   private static class TestCallableTask implements Callable<Boolean>, Serializable {
      public TestCallableTask(String key) {
         this.key = key;
      }

      @Override
      public Boolean call() throws Exception {
         ignite1.getMap("cache").put(key, Boolean.TRUE);
         return true;
      }

      String key;
   }

   private static class TestRunnableTask implements Runnable, Serializable {
      public TestRunnableTask(String key) {
         this.key = key;
      }

      @Override
      public void run() {
         ignite1.getMap("cache").put(key, Boolean.TRUE);
      }

      String key;
   }
}
