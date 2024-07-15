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

import inetsoft.sree.internal.cluster.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class IgniteMultiMapTest {
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
      MultiMap<String, String> map1 = ignite1.getMultiMap("multiMapAddRemove");
      MultiMap<String, String> map2 = ignite2.getMultiMap("multiMapAddRemove");
      map1.put("key1", "value1");
      map1.put("key1", "value2");
      map1.put("key1", "value3");
      map1.put("key2", "value1");

      assertEquals(4, map2.size());

      Collection<String> list = map2.get("key1");
      assertEquals(3, list.size());

      map1.remove("key1", "value1");
      assertEquals(3, map2.size());

      list = map2.get("key1");
      assertEquals(2, list.size());

      map1.remove("key1");
      assertEquals(1, map2.size());
   }

   @Test
   void lockUnlock() {
      // same thread needs to lock, unlock on the same lock instance
      MultiMap<Object, Object> map = ignite1.getMultiMap("multiMapLockUnlock");
      map.lock("key1");

      assertDoesNotThrow(() -> {
         map.unlock("key1");
      });
   }

   @Test
   void contains() {
      MultiMap<String, String> map1 = ignite1.getMultiMap("multiMapContains");
      MultiMap<String, String> map2 = ignite2.getMultiMap("multiMapContains");
      map1.put("key1", "value1");
      map1.put("key1", "value2");
      map1.put("key1", "value3");
      map1.put("key2", "value1");
      map1.put("key2", "value2");

      assertTrue(map2.containsValue("value1"));
      assertTrue(map2.containsValue("value3"));
      assertTrue(map2.containsEntry("key1", "value1"));
      assertTrue(map2.containsEntry("key2", "value1"));
      assertFalse(map2.containsEntry("key2", "value3"));
      assertTrue(map2.containsKey("key1"));
      assertTrue(map2.containsKey("key2"));
      assertFalse(map2.containsKey("key3"));
      assertEquals(3, map2.valueCount("key1"));
      assertEquals(2, map2.valueCount("key2"));
      assertEquals(2, map2.keySet().size());
      assertEquals(5, map2.values().size());
      assertEquals(5, map2.entrySet().size());

      Collection<String> list = map2.get("key1");
      assertEquals(3, list.size());
      assertTrue(list.contains("value1"));
      assertTrue(list.contains("value2"));
      assertTrue(list.contains("value3"));
   }


   @Test
   void multiMapListener() throws InterruptedException {
      CountDownLatch addedLatch = new CountDownLatch(1);
      CountDownLatch updatedLatch = new CountDownLatch(1);
      CountDownLatch removedLatch = new CountDownLatch(1);
      AtomicInteger added = new AtomicInteger();
      AtomicInteger updated = new AtomicInteger();
      AtomicInteger removed = new AtomicInteger();
      MultiMap<String, String> map = ignite1.getMultiMap("multiMapListener");

      ignite2.addMultiMapListener("multiMapListener", new MapChangeListener<String, Collection<String>>() {
         @Override
         public void entryAdded(EntryEvent<String, Collection<String>> event) {
            added.incrementAndGet();
            addedLatch.countDown();
         }

         @Override
         public void entryUpdated(EntryEvent<String, Collection<String>> event) {
            updated.incrementAndGet();
            updatedLatch.countDown();
         }

         @Override
         public void entryRemoved(EntryEvent<String, Collection<String>> event) {
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
