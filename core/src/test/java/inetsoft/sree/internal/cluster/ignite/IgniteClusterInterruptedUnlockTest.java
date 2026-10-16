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

import inetsoft.test.*;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78096, an Ignite lock that is unlocked on an interrupted thread threw
 * IgniteInterruptedException and stayed held by that thread until its node left the cluster, so
 * every later lock() of it on any thread or node waited forever. The lock must be released and
 * the interrupt kept, for the locks of getLock() and for the key and read/write locks, which are
 * released by unlockLock().
 *
 * The nodes are real Ignite nodes with the product configuration, the interrupt is the only
 * thing the test sets.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IgniteClusterInterruptedUnlockTest {
   @BeforeAll
   static void startNodes() throws Exception {
      clusterDir = Files.createTempDirectory("cluster-78096");
      int port1 = freePort();
      int port2 = freePort();
      node = startNode("u78096-" + UUID.randomUUID(), port1, port1, port2);
      // a lock that is held on one node is held for every node
      otherNode = startNode("u78096-other-" + UUID.randomUUID(), port2, port1, port2);
      long end = System.currentTimeMillis() + 30000L;

      while(node.getClusterNodeIds().size() != 2 && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      assertEquals(2, node.getClusterNodeIds().size());
   }

   @AfterAll
   static void stopNodes() throws Exception {
      if(otherNode != null) {
         otherNode.close();
         otherNode = null;
      }

      if(node != null) {
         node.close();
         node = null;
      }

      if(clusterDir != null) {
         try(var paths = Files.walk(clusterDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   @AfterEach
   void clearInterrupt() {
      Thread.interrupted();
   }

   @Test
   void lockIsReleasedWhenUnlockedOnInterruptedThread() throws Exception {
      String name = "t78096.lock." + UUID.randomUUID();
      Lock lock = node.getLock(name);
      lock.lock();
      Thread.currentThread().interrupt();
      lock.unlock();

      assertTrue(Thread.interrupted(), "interrupt was lost");
      assertTrue(isFreeOnOtherThread(name), "lock is still held after it was unlocked");
      assertTrue(isFreeOnOtherNode(name), "lock is still held on the other node");
   }

   @Test
   void keyLockIsReleasedWhenUnlockedOnInterruptedThread() throws Exception {
      String name = "t78096.key." + UUID.randomUUID();
      node.lockKey(name);
      Thread.currentThread().interrupt();
      node.unlockKey(name);

      assertTrue(Thread.interrupted(), "interrupt was lost");
      assertTrue(isFreeOnOtherThread(name), "key lock is still held after it was unlocked");
      assertTrue(isFreeOnOtherNode(name), "key lock is still held on the other node");
   }

   @Test
   void writeLockIsReleasedWhenUnlockedOnInterruptedThread() throws Exception {
      String name = "t78096.rw." + UUID.randomUUID();
      node.lockWrite(name);
      Thread.currentThread().interrupt();
      node.unlockWrite(name);

      assertTrue(Thread.interrupted(), "interrupt was lost");
      assertTrue(isFreeOnOtherThread("write." + name),
                 "write lock is still held after it was unlocked");
      assertTrue(isFreeOnOtherNode("write." + name), "write lock is still held on the other node");
   }

   private static boolean isFreeOnOtherThread(String name) throws Exception {
      return isFree(node, name);
   }

   private static boolean isFreeOnOtherNode(String name) throws Exception {
      return isFree(otherNode, name);
   }

   private static boolean isFree(IgniteCluster cluster, String name) throws Exception {
      FutureTask<Boolean> task = new FutureTask<>(() -> {
         Lock lock = cluster.getLock(name);

         if(lock.tryLock(5, TimeUnit.SECONDS)) {
            lock.unlock();
            return true;
         }

         return false;
      });

      Thread thread = new Thread(task, "t78096-other");
      thread.start();
      return task.get(30, TimeUnit.SECONDS);
   }

   private static IgniteCluster startNode(String instance, int discoPort, int... ports)
      throws Exception
   {
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Arrays.stream(ports).mapToObj(p -> "127.0.0.1:" + p).toList());
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(freePort());
      comm.setLocalPortRange(0);

      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir.resolve(instance));
      config.setIgniteInstanceName(instance);
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      return IgniteClusterTestUtils.getIgniteCluster(config);
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static Path clusterDir;
   private static IgniteCluster node;
   private static IgniteCluster otherNode;
}
