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

import inetsoft.sree.internal.cluster.SingletonCallableTask;
import inetsoft.test.*;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77383: the singleton {@link ServiceTaskExecutorImpl} looks up its task queue with a null
 * configuration. When the queue is not visible yet (all nodes using a new service id at the same
 * moment) or has been removed, the lookup returns null or a removed queue. The executor must keep
 * looking for the queue the submitters use instead of failing on every poll, which left every
 * task submitted to the service waiting until its caller timed out.
 *
 * <p>Tasks are submitted through {@link IgniteCluster#submit(String, SingletonCallableTask)}, so
 * they are offered through {@link IgniteCluster#getQueue(String)} exactly as in production.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ServiceTaskExecutorImplTest {
   @TempDir
   static Path clusterDir;

   @BeforeAll
   static void startCluster() throws Exception {
      int discoPort = freePort();
      int commPort = freePort();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);

      IgniteConfiguration config = IgniteCluster.getDefaultConfig(clusterDir);
      config.setIgniteInstanceName("bug77383-" + UUID.randomUUID());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      cluster = new IgniteCluster(config);
   }

   @AfterAll
   static void stopCluster() {
      if(cluster != null) {
         cluster.close();
         cluster = null;
      }
   }

   // The executor is started before its queue exists, so init() gets a null queue. A task
   // submitted afterwards must still run.
   @Test
   void executorStartedBeforeQueueExistsRunsSubmittedTask() throws Exception {
      String serviceId = "bug77383-early-" + UUID.randomUUID();
      cluster.getIgniteInstance().services()
         .deployClusterSingleton(serviceId, new ServiceTaskExecutorImpl(serviceId));

      assertEquals("ran", cluster.submit(serviceId, new EchoTask("ran"))
         .get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
   }

   // The queue is removed while the executor runs and the next submit creates it again. The
   // executor must follow the new queue instead of polling the removed one.
   @Test
   void executorFollowsQueueRecreatedAfterRemoval() throws Exception {
      String serviceId = "bug77383-removed-" + UUID.randomUUID();

      assertEquals("first", cluster.submit(serviceId, new EchoTask("first"))
         .get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));

      cluster.destroyQueue(ServiceTaskExecutorImpl.QUEUE_PREFIX + serviceId);

      assertEquals("second", cluster.submit(serviceId, new EchoTask("second"))
         .get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static final class EchoTask implements SingletonCallableTask<String> {
      EchoTask(String value) {
         this.value = value;
      }

      @Override
      public String call() {
         return value;
      }

      private final String value;
   }

   private static final long TASK_TIMEOUT_SECONDS = 20L;
   private static IgniteCluster cluster;
}
