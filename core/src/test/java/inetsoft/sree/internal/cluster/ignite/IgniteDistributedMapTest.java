/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class IgniteDistributedMapTest {
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
   void addAndRemove() {
      DistributedMap<Object, Object> map1 = ignite1.getMap("addAndRemove");
      DistributedMap<Object, Object> map2 = ignite2.getMap("addAndRemove");
      map1.put("key1", "value1");
      map1.put("key2", "value2");
      assertEquals(2, map2.size());

      map1.remove("key1");
      assertEquals(1, map2.size());
   }

   @Test
   void lockUnlock() {
      // same thread needs to lock, unlock on the same lock instance
      DistributedMap<Object, Object> map = ignite1.getMap("lockUnlock");
      map.lock("key1");

      assertDoesNotThrow(() -> {
         map.unlock("key1");
      });
   }


   @Test
   void basicFunctionality() {
      DistributedMap<Object, Object> map1 = ignite1.getMap("basicFunctionality");
      DistributedMap<Object, Object> map2 = ignite2.getMap("basicFunctionality");
      map1.put("key1", "value1");
      map1.put("key2", "value2");
      map1.set("key3", "value3");

      assertEquals("value2", map2.get("key2"));
      assertEquals("value3", map2.get("key3"));
      assertEquals(3, map2.size());
      assertTrue(map2.containsKey("key2"));
      assertFalse(map2.containsKey("key5"));
      assertTrue(map2.containsValue("value3"));

      map1.clear();
      assertEquals(0, map2.size());
   }

   @Test
   void distributedMapListener() throws InterruptedException {
      CountDownLatch addedLatch = new CountDownLatch(1);
      CountDownLatch updatedLatch = new CountDownLatch(1);
      CountDownLatch removedLatch = new CountDownLatch(1);
      AtomicInteger added = new AtomicInteger();
      AtomicInteger updated = new AtomicInteger();
      AtomicInteger removed = new AtomicInteger();
      DistributedMap<String, String> map = ignite1.getMap("mapListener");

      ignite2.addMapListener("mapListener", new MapChangeListener<String, String>() {
         @Override
         public void entryAdded(EntryEvent<String, String> event) {
            added.incrementAndGet();
            addedLatch.countDown();
         }

         @Override
         public void entryUpdated(EntryEvent<String, String> event) {
            updated.incrementAndGet();
            updatedLatch.countDown();
         }

         @Override
         public void entryRemoved(EntryEvent<String, String> event) {
            removed.incrementAndGet();
            removedLatch.countDown();
         }
      });

      map.put("key", "new");
      map.put("key", "updated");
      map.remove("key");

      assertTrue(addedLatch.await(2, TimeUnit.SECONDS));
      assertTrue(updatedLatch.await(2, TimeUnit.SECONDS));
      assertTrue(removedLatch.await(2, TimeUnit.SECONDS));
      assertEquals(0, map.size());
      assertThat(added.get(), greaterThan(0));
      assertThat(updated.get(), greaterThan(0));
      assertThat(removed.get(), greaterThan(0));
   }

   @Test
   void replicatedMapListener() throws InterruptedException {
      CountDownLatch addedLatch = new CountDownLatch(1);
      CountDownLatch updatedLatch = new CountDownLatch(1);
      CountDownLatch removedLatch = new CountDownLatch(1);
      AtomicInteger added = new AtomicInteger();
      AtomicInteger updated = new AtomicInteger();
      AtomicInteger removed = new AtomicInteger();
      DistributedMap<String, String> map = ignite1.getReplicatedMap("replicatedMapListener");

      ignite2.addReplicatedMapListener("replicatedMapListener", new MapChangeListener<String, String>() {
         @Override
         public void entryAdded(EntryEvent<String, String> event) {
            added.incrementAndGet();
            addedLatch.countDown();
         }

         @Override
         public void entryUpdated(EntryEvent<String, String> event) {
            updated.incrementAndGet();
            updatedLatch.countDown();
         }

         @Override
         public void entryRemoved(EntryEvent<String, String> event) {
            removed.incrementAndGet();
            removedLatch.countDown();
         }
      });

      map.put("key", "new");
      map.put("key", "updated");
      map.remove("key");

      assertTrue(addedLatch.await(2, TimeUnit.SECONDS));
      assertTrue(updatedLatch.await(2, TimeUnit.SECONDS));
      assertTrue(removedLatch.await(2, TimeUnit.SECONDS));
      assertEquals(0, map.size());
      assertThat(added.get(), greaterThan(0));
      assertThat(updated.get(), greaterThan(0));
      assertThat(removed.get(), greaterThan(0));
   }

   private static IgniteCluster ignite1;
   private static IgniteCluster ignite2;
}
