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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteClusterTestUtils;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.GlobalExceptionHandler;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78559, a key-value store deleted on one node, e.g. when an organization is deleted, must
 * still be readable through the instance another node holds. Runs two real Ignite nodes: node A
 * is the Spring cluster bean, so the storage tasks run against real Ignite caches, and node B
 * holds its own {@link KeyValueStorageManager}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, LocalKeyValueStorageDeletedStoreTest.NodeA.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class LocalKeyValueStorageDeletedStoreTest {
   @TempDir
   static Path clusterDir;

   private static final int[] PORTS = { freePort(), freePort() };

   @Configuration
   static class NodeA {
      @Bean(destroyMethod = "close")
      public Cluster cluster() throws Exception {
         return start("A", PORTS[0]);
      }
   }

   @Test
   void readsOnAnotherNodeSurviveStoreDeletion() throws Exception {
      IgniteCluster a = (IgniteCluster) ConfigurationContext.getContext().getSpringBean(Cluster.class);
      KeyValueEngine engine = ConfigurationContext.getContext().getSpringBean(KeyValueEngine.class);
      IgniteCluster b = start("B", PORTS[1]);
      KeyValueStorageManager kvB = null;
      boolean bClosed = false;

      try {
         waitForServers(a, 2);
         KeyValueStorageManager kvA = KeyValueStorageManager.getInstance();
         kvB = new KeyValueStorageManager(engine, b);

         String id = "bug78559__dashboards";
         KeyValueStorage<String> storeA = kvA.getStorage(id);
         KeyValueStorage<String> storeB = kvB.getStorage(id);
         storeA.put("k", "old-org").get(1, TimeUnit.MINUTES);
         assertEquals("old-org", storeB.get("k"));

         // as DashboardManager.removeDashboardStorage does on the node that deletes the org
         deleteStore(kvA, id);

         // node B still holds the same, not closed, instance
         assertSame(storeB, kvB.getStorage(id));
         assertFalse(storeB.isClosed());
         assertNull(storeB.get("k"));
         assertFalse(storeB.contains("k"));
         assertEquals(0, storeB.size());
         assertEquals(List.of(), storeB.keys().toList());
         assertEquals(List.of(), storeB.stream().toList());

         // the deleted content does not come back on any node
         KeyValueStorage<String> newStoreA = kvA.getStorage(id);
         assertNotSame(storeA, newStoreA);
         assertNull(newStoreA.get("k"));

         // the re-created organization's content is read through the stale instance
         newStoreA.put("k2", "new-org").get(1, TimeUnit.MINUTES);
         assertEquals("new-org", storeB.get("k2"));
         assertNull(storeB.get("k"));

         // a second deletion, read first by keys() and stream(), which are consumed lazily
         deleteStore(kvA, id);
         assertEquals(List.of(), kvB.<String>getStorage(id).keys().toList());
         deleteStore(kvA, id);
         assertEquals(List.of(), kvB.<String>getStorage(id).stream().toList());

         // a write through the stale instance after a deletion reaches every node
         deleteStore(kvA, id);
         storeB.put("k3", "written-on-b").get(1, TimeUnit.MINUTES);
         assertEquals("written-on-b", storeB.get("k3"));
         assertEquals("written-on-b", kvA.<String>getStorage(id).get("k3"));

         // a node that is shut down still fails with CacheStoppedException (503 "System is
         // shutting down") instead of reading an empty store
         // closing the node also removes the listeners of its storages
         bClosed = true;
         b.close();
         RuntimeException thrown = assertThrows(RuntimeException.class, () -> storeB.get("k2"));
         assertTrue(GlobalExceptionHandler.isCacheStoppedException(thrown), () -> chain(thrown));
      }
      finally {
         if(!bClosed) {
            if(kvB != null) {
               kvB.close();
            }

            b.close();
         }
      }
   }

   private static void deleteStore(KeyValueStorageManager manager, String id) throws Exception {
      manager.<String>getStorage(id).deleteStore().get(1L, TimeUnit.MINUTES);
      manager.<String>getStorage(id).close();
   }

   private static void waitForServers(IgniteCluster cluster, int count) throws Exception {
      long end = System.currentTimeMillis() + 60000L;

      while(cluster.getIgniteInstance().cluster().forServers().nodes().size() < count) {
         assertTrue(System.currentTimeMillis() < end, "The second node did not join");
         Thread.sleep(200L);
      }
   }

   private static IgniteCluster start(String name, int port) throws Exception {
      List<String> addresses = new ArrayList<>();

      for(int p : PORTS) {
         addresses.add("127.0.0.1:" + p);
      }

      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(addresses);
      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(port);
      discovery.setLocalPortRange(0);
      discovery.setIpFinder(ipFinder);
      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(freePort());
      communication.setLocalPortRange(0);
      IgniteConfiguration config =
         IgniteCluster.getDefaultConfig(Files.createDirectories(clusterDir.resolve(name)));
      config.setIgniteInstanceName("bug78559-" + name + "-" + UUID.randomUUID());
      config.setLocalHost("127.0.0.1");
      config.setConsistentId(name);
      config.setDiscoverySpi(discovery);
      config.setCommunicationSpi(communication);
      return IgniteClusterTestUtils.getIgniteCluster(config);
   }

   private static int freePort() {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static String chain(Throwable t) {
      StringBuilder sb = new StringBuilder();

      for(Throwable c = t; c != null; c = c.getCause()) {
         sb.append(" <- ").append(c.getClass().getName()).append(": ").append(c.getMessage());
      }

      return sb.toString();
   }
}
