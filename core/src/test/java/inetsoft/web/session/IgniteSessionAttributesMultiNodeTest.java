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
package inetsoft.web.session;

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteDistributedMap;
import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.util.ConfigurationContext;
import inetsoft.web.admin.server.NodeProtectionService;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78108: the attributes of all sessions are in one replicated Ignite cache. Runs two
 * embedded server nodes and one client node (as a cloud runner is) in one JVM, each with its own
 * cluster, and checks that the attributes of a session written on one node are seen on the
 * others, that a client node, which holds no partitions, lists a session's attribute names, that
 * the purge removes them on every node, and that none of this changes the topology version.
 * Tagged slow: starting three nodes and the Spring context takes about 10 s.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class IgniteSessionAttributesMultiNodeTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      int[] ports = freePorts(6);
      discoveryAddresses = List.of(
         "127.0.0.1:" + ports[0], "127.0.0.1:" + ports[1], "127.0.0.1:" + ports[2]);
      serverA = start("A", workDir, ports[0], ports[3], discoveryAddresses, false);
      serverB = start("B", workDir, ports[1], ports[4], discoveryAddresses, false);
      client = start("C", workDir, ports[2], ports[5], discoveryAddresses, true);
   }

   @AfterAll
   static void stopIgnite() {
      for(Ignite ignite : new Ignite[] { client, serverB, serverA }) {
         if(ignite != null) {
            ignite.close();
         }
      }

      client = serverB = serverA = null;
   }

   @BeforeEach
   void setUp() {
      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      sessionsCluster = new MockCluster();
      scheduledExecutor = mock(DistributedScheduledExecutorService.class);
      clusterA = new NodeCluster(serverA);
      clusterB = new NodeCluster(serverB);
      clusterC = new NodeCluster(client);

      on(clusterA);
      repositoryA = repository(clusterA);
      on(clusterB);
      repositoryB = repository(clusterB);
      // start the attribute cache on the client node
      on(clusterC);
      IgniteSessionRepository.getSessionAttributeMap("unknown");
   }

   @AfterEach
   void tearDown() throws Exception {
      on(clusterA);
      repositoryA.destroy();
      on(clusterB);
      repositoryB.destroy();
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);

      for(String name : serverA.cacheNames()) {
         serverA.cache(name).clear();
      }
   }

   @Test
   void attributes_areSharedAcrossServerAndClientNodes_withoutExchange() {
      AffinityTopologyVersion before = readyAffinityVersion(serverA);
      List<String> ids = new ArrayList<>();

      for(int i = 0; i < 10; i++) {
         on(clusterA);
         IgniteSessionRepository.IgniteSession session = repositoryA.createSession();
         session.setAttribute("a", "value-" + i);
         repositoryA.save(session);
         ids.add(session.getId());
      }

      for(int i = 0; i < ids.size(); i++) {
         String id = ids.get(i);

         on(clusterB);
         DistributedMap<String, Object> mapB = IgniteSessionRepository.getSessionAttributeMap(id);
         assertNotNull(mapB, "a session created on A has its attributes on B");
         assertEquals("value-" + i, mapB.get("IgniteSession.ATTR.a"));
         mapB.put("fromB", i);

         on(clusterC);
         DistributedMap<String, Object> mapC = IgniteSessionRepository.getSessionAttributeMap(id);
         assertNotNull(mapC, "a session created on A has its attributes on the client node");
         assertEquals(Set.of("IgniteSession.ATTR.a", "fromB"), mapC.keySet(),
                      "the client node lists only the session's own attributes");
         mapC.put("fromC", i);

         on(clusterA);
         assertEquals(Set.of("IgniteSession.ATTR.a", "fromB", "fromC"),
                      IgniteSessionRepository.getSessionAttributeMap(id).keySet());
         assertEquals(i, IgniteSessionRepository.getSessionAttributeMap(id).get("fromC"));
      }

      // end the sessions on B; the purge is scheduled and then run on the client node
      on(clusterB);

      for(String id : ids) {
         repositoryB.invalidateSession(id);
      }

      ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
      verify(scheduledExecutor, timeout(10000).atLeast(ids.size()))
         .scheduleWithId(startsWith("destroy-map-"), tasks.capture(), eq(10L),
                         eq(TimeUnit.MINUTES));

      on(clusterC);

      for(Runnable task : tasks.getAllValues()) {
         task.run();
      }

      for(NodeCluster cluster : List.of(clusterA, clusterB, clusterC)) {
         on(cluster);

         for(String id : ids) {
            assertNull(IgniteSessionRepository.getSessionAttributeMap(id),
                       "the purge removes the attributes on every node");
         }
      }

      assertEquals(before, readyAffinityVersion(serverA),
                   "a session lifecycle must not cause a partition map exchange");
      assertEquals(before, readyAffinityVersion(serverB));
      assertEquals(before, readyAffinityVersion(client));
      assertEquals(0, serverB.cache(SESSION_ATTRIBUTES).size(), "nothing is left in the cache");
   }

   /**
    * A server node that has just joined lists a session's attribute names from its own copy of
    * the partition, which it gets from the other nodes when it starts.
    */
   @Test
   void joiningServerNode_listsAttributesOfExistingSessions(@TempDir Path workDir) {
      List<String> ids = new ArrayList<>();
      on(clusterA);

      for(int i = 0; i < 200; i++) {
         IgniteSessionRepository.IgniteSession session = repositoryA.createSession();
         session.setAttribute("a", i);
         session.setAttribute("b" + i, i);
         repositoryA.save(session);
         ids.add(session.getId());
      }

      int[] ports = freePorts(2);
      List<String> discovery = new ArrayList<>(discoveryAddresses);
      discovery.add("127.0.0.1:" + ports[0]);

      try(Ignite serverD = start("D", workDir, ports[0], ports[1], discovery, false)) {
         NodeCluster clusterD = new NodeCluster(serverD);
         on(clusterD);

         for(int i = 0; i < ids.size(); i++) {
            DistributedMap<String, Object> map =
               IgniteSessionRepository.getSessionAttributeMap(ids.get(i));
            assertNotNull(map);
            assertEquals(Set.of("IgniteSession.ATTR.a", "IgniteSession.ATTR.b" + i), map.keySet());
         }
      }
   }

   private IgniteSessionRepository repository(Cluster cluster) {
      IgniteSessionRepository repository = new IgniteSessionRepository(
         mock(SecurityEngine.class), mock(AuthenticationService.class),
         mock(NodeProtectionService.class), cluster);
      repository.afterPropertiesSet();
      return repository;
   }

   /**
    * Makes the given node's cluster the one that Cluster.getInstance() returns.
    */
   @SuppressWarnings("unchecked")
   private void on(Cluster cluster) {
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(any(Class.class)))
         .thenAnswer(inv -> savedAppContext.getBean((Class<Object>) inv.getArgument(0)));
      doReturn(cluster).when(context).getBean(Cluster.class);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   private static Ignite start(String name, Path workDir, int discoveryPort, int communicationPort,
                               List<String> discoveryAddresses, boolean clientMode)
   {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("IgniteSessionAttributesMultiNodeTest-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      config.setClientMode(clientMode);
      IgniteUtils.configBinaryTypes(config);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(discoveryPort);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(discoveryAddresses);
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(communicationPort);
      communication.setLocalPortRange(0);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      return Ignition.start(config);
   }

   private static AffinityTopologyVersion readyAffinityVersion(Ignite ignite) {
      return ((IgniteEx) ignite).context().cache().context().exchange().readyAffinityVersion();
   }

   private static int[] freePorts(int count) {
      ServerSocket[] sockets = new ServerSocket[count];
      int[] ports = new int[count];

      try {
         for(int i = 0; i < count; i++) {
            sockets[i] = new ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
         }
      }
      catch(IOException ex) {
         throw new UncheckedIOException(ex);
      }
      finally {
         for(ServerSocket socket : sockets) {
            if(socket != null) {
               try {
                  socket.close();
               }
               catch(IOException ignore) {
               }
            }
         }
      }

      return ports;
   }

   /**
    * The cluster of one node: its replicated maps are the node's Ignite caches with the
    * product's configuration. The sessions cache, its listeners and the scheduled executor are
    * shared by all the nodes and stay in memory.
    */
   private final class NodeCluster extends MockCluster {
      NodeCluster(Ignite ignite) {
         this.ignite = ignite;
      }

      @SuppressWarnings("unchecked")
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         try {
            Method method = IgniteCluster.class.getDeclaredMethod(
               "getCacheConfiguration", String.class, CacheMode.class, int.class);
            method.setAccessible(true);
            CacheConfiguration<K, V> config =
               (CacheConfiguration<K, V>) method.invoke(null, name, CacheMode.REPLICATED, 2);
            return new IgniteDistributedMap<>(ignite.getOrCreateCache(config));
         }
         catch(ReflectiveOperationException e) {
            throw new IllegalStateException(e);
         }
      }

      @Override
      public <K, V> javax.cache.Cache<K, V> getCache(String name, boolean replicated,
                                                     javax.cache.expiry.ExpiryPolicy expiryPolicy)
      {
         return sessionsCluster.getCache(name, replicated, expiryPolicy);
      }

      @Override
      public <K, V> void addReplicatedMapListener(String name, MapChangeListener<K, V> l) {
         sessionsCluster.addReplicatedMapListener(name, l);
      }

      @Override
      public void removeReplicatedMapListener(String name, MapChangeListener<?, ?> l) {
         sessionsCluster.removeReplicatedMapListener(name, l);
      }

      @Override
      public DistributedScheduledExecutorService getScheduledExecutor() {
         return scheduledExecutor;
      }

      private final Ignite ignite;
   }

   private static final String SESSION_ATTRIBUTES =
      IgniteSessionRepository.class.getName() + ".sessionAttributes";
   private static List<String> discoveryAddresses;
   private static Ignite serverA;
   private static Ignite serverB;
   private static Ignite client;
   private ApplicationContext savedAppContext;
   private MockCluster sessionsCluster;
   private DistributedScheduledExecutorService scheduledExecutor;
   private NodeCluster clusterA;
   private NodeCluster clusterB;
   private NodeCluster clusterC;
   private IgniteSessionRepository repositoryA;
   private IgniteSessionRepository repositoryB;
}
