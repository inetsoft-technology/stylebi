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
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedTransactionException;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.transactions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77879: {@link IgniteCluster#runInTransaction} and {@link IgniteDistributedMap#getForUpdate}
 * replace explicit cache-entry locks. A key read in the transaction stays locked until it ends,
 * waiting for a lock is bounded by the timeout, a nested call joins the outer transaction, and a
 * failure rolls back every write. One embedded node with the product's replicated cache
 * configuration.
 */
@Tag("core")
class IgniteClusterRunInTransactionTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) throws Exception {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("IgniteClusterRunInTransactionTest");
      config.setWorkDirectory(workDir.toString());
      config.setMetricsLogFrequency(0);
      IgniteUtils.configBinaryTypes(config);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(48750);
      discovery.setLocalPortRange(5);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:48750..48754"));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(48850);
      communication.setLocalPortRange(10);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      ignite = Ignition.start(config);

      Method method = IgniteCluster.class.getDeclaredMethod(
         "getCacheConfiguration", String.class, CacheMode.class, int.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked")
      CacheConfiguration<String, String> cacheConfig = (CacheConfiguration<String, String>)
         method.invoke(null, "IgniteClusterRunInTransactionTest", CacheMode.REPLICATED, 2);
      map = new IgniteDistributedMap<>(ignite.getOrCreateCache(cacheConfig));
   }

   @AfterAll
   static void stopIgnite() {
      if(ignite != null) {
         ignite.close();
         ignite = null;
      }
   }

   @BeforeEach
   void clear() {
      map.clear();
   }

   @Test
   void commitsTheWritesOfTheAction() {
      map.put("key", "old");

      String read = IgniteCluster.runInTransaction(ignite, 10, TimeUnit.SECONDS, () -> {
         String value = map.getForUpdate("key");
         map.set("key", "new");
         return value;
      });

      assertEquals("old", read);
      assertEquals("new", map.get("key"));
   }

   /**
    * A key locked by one transaction cannot be locked by another until the first ends; the
    * second times out with nothing committed.
    */
   @Test
   void waitingForALockTimesOut() throws Exception {
      CountDownLatch locked = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<?> holder = executor.submit(() -> IgniteCluster.runInTransaction(
            ignite, 30, TimeUnit.SECONDS, () -> {
               map.getForUpdate("key");
               locked.countDown();
               release.await();
               map.set("key", "holder");
               return null;
            }));
         assertTrue(locked.await(10, TimeUnit.SECONDS));

         assertThrows(DistributedTransactionException.class, () -> IgniteCluster.runInTransaction(
            ignite, 500, TimeUnit.MILLISECONDS, () -> {
               map.getForUpdate("key");
               map.set("key", "waiter");
               return null;
            }));

         release.countDown();
         holder.get(10, TimeUnit.SECONDS);
         assertEquals("holder", map.get("key"));
      }
      finally {
         release.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * A nested call joins the outer transaction instead of starting one, which Ignite refuses, and
    * its writes commit with the outer ones.
    */
   @Test
   void nestedCallJoinsTheTransaction() {
      IgniteCluster.runInTransaction(ignite, 10, TimeUnit.SECONDS, () -> {
         map.getForUpdate("outer");
         map.set("outer", "outer");

         IgniteCluster.runInTransaction(ignite, 10, TimeUnit.SECONDS, () -> {
            assertNotNull(ignite.transactions().tx(), "the nested call left the transaction");
            map.getForUpdate("inner");
            map.set("inner", "inner");
            return null;
         });

         return null;
      });

      assertEquals("outer", map.get("outer"));
      assertEquals("inner", map.get("inner"));
      assertNull(ignite.transactions().tx());
   }

   /**
    * A nested call that fails marks the transaction rollback-only, even if the outer action
    * catches the failure.
    */
   @Test
   void failedNestedCallRollsBackTheTransaction() {
      assertThrows(DistributedTransactionException.class, () -> IgniteCluster.runInTransaction(
         ignite, 10, TimeUnit.SECONDS, () -> {
            map.set("outer", "outer");

            try {
               IgniteCluster.runInTransaction(ignite, 10, TimeUnit.SECONDS, () -> {
                  map.set("inner", "inner");
                  throw new IllegalArgumentException("inner failed");
               });
            }
            catch(IllegalArgumentException ignore) {
            }

            return null;
         }));

      assertNull(map.get("outer"));
      assertNull(map.get("inner"));
   }

   /**
    * A checked exception from the action rolls back its writes and is thrown as it is.
    */
   @Test
   void checkedExceptionRollsBack() {
      Exception thrown = assertThrows(Exception.class, () -> IgniteCluster.runInTransaction(
         ignite, 10, TimeUnit.SECONDS, () -> {
            map.getForUpdate("key");
            map.set("key", "value");
            throw new Exception("action failed");
         }));

      assertEquals("action failed", thrown.getMessage());
      assertNull(map.get("key"));
      assertNull(ignite.transactions().tx());
   }

   /**
    * The action cannot join an optimistic transaction, whose reads do not lock.
    */
   @Test
   void refusesToJoinAnOptimisticTransaction() {
      try(Transaction ignored = ignite.transactions().txStart(
         TransactionConcurrency.OPTIMISTIC, TransactionIsolation.SERIALIZABLE))
      {
         assertThrows(IllegalStateException.class, () -> IgniteCluster.runInTransaction(
            ignite, 10, TimeUnit.SECONDS, () -> null));
      }
   }

   private static Ignite ignite;
   private static IgniteDistributedMap<String, String> map;
}
