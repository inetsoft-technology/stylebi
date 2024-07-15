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

import inetsoft.sree.internal.cluster.DistributedReference;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.Serializable;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class IgniteDistributedReferenceTest {
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
   void distributedInteger() {
      DistributedReference<Integer> int1 = ignite1.getReference("integerRef");
      DistributedReference<Integer> int2 = ignite2.getReference("integerRef");

      int1.set(10);
      assertEquals(10, int2.get());

      int2.compareAndSet(10, 20);
      assertEquals(20, int1.getAndSet(30));

      assertEquals(30, int2.get());
   }

   @Test
   void distributedSerializableObject() {
      DistributedReference<TestObject> obj1 = ignite1.getReference("objectRef");
      DistributedReference<TestObject> obj2 = ignite2.getReference("objectRef");

      obj1.set(new TestObject("message"));

      assertEquals("message", obj2.get().message);
   }

   private static IgniteCluster ignite1;
   private static IgniteCluster ignite2;

   private static class TestObject implements Serializable {
      public TestObject(String message) {
         this.message = message;
      }

      private String message;
   }
}
