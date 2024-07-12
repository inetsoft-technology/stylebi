/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.sree.internal.cluster.ignite;

import inetsoft.sree.internal.cluster.DistributedLong;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Disabled
public class IgniteDistributedLongTest {
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
   void testDistributedLong() {
      DistributedLong long1 = ignite1.getLong("distLong");
      DistributedLong long2 = ignite2.getLong("distLong");

      long1.set(10);
      assertEquals(10, long2.get());
      assertEquals(15, long1.addAndGet(5));
      assertTrue(long2.compareAndSet(15, 20));
      assertEquals(20, long1.get());
      assertEquals(21, long2.incrementAndGet());
      assertEquals(21, long1.getAndDecrement());
      assertEquals(20, long2.get());
   }

   @Test
   void destroyLong() {
      DistributedLong long1 = ignite1.getLong("destroyLong");
      DistributedLong long2 = ignite2.getLong("destroyLong");

      long1.set(10);
      ignite1.destroyLong("destroyLong");
      assertThrows(Exception.class, () -> long2.set(20));
   }

   private static IgniteCluster ignite1;
   private static IgniteCluster ignite2;
}
