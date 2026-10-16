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

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78108: every HTTP session used to start its own replicated Ignite cache for its
 * attributes and destroy it 10 minutes after the session ended, which is two cluster-wide
 * partition map exchanges per session. The attributes of all sessions are now in one cache. Runs
 * on one embedded Ignite node with the product's replicated cache configuration and binary
 * types, so the attribute cache, its partition scans and the exchanges are Ignite's.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IgniteSessionAttributesTopologyTest {
   @BeforeAll
   static void startIgnite(@TempDir Path workDir) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("IgniteSessionAttributesTopologyTest");
      config.setWorkDirectory(workDir.toString());
      config.setMetricsLogFrequency(0);
      IgniteUtils.configBinaryTypes(config);

      int[] ports = freePorts(2);
      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(ports[0]);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + ports[0]));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(ports[1]);
      communication.setLocalPortRange(0);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration()
         .setInitialSize(64L << 20)
         .setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      ignite = Ignition.start(config);
   }

   @AfterAll
   static void stopIgnite() {
      if(ignite != null) {
         ignite.close();
         ignite = null;
      }
   }

   /**
    * The Spring context of the class stays in place for the code that reaches other beans; only
    * the cluster is replaced, for each test, by one backed by the embedded node.
    */
   @BeforeEach
   void setUp() {
      for(String name : ignite.cacheNames()) {
         ignite.cache(name).clear();
      }

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      cluster = new IgniteAttributesCluster();
      installContext(cluster, savedAppContext);
      repository = new IgniteSessionRepository(
         mock(SecurityEngine.class), mock(AuthenticationService.class),
         mock(NodeProtectionService.class), cluster);
      repository.afterPropertiesSet();
   }

   @AfterEach
   void tearDown() throws Exception {
      repository.destroy();
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   /**
    * Creating, saving, invalidating and purging sessions, and the sweep, must not start or
    * destroy a cache: the Ignite topology version, minor version included, stays the same.
    */
   @Test
   void sessionLifecycle_doesNotChangeTopologyVersion() {
      AffinityTopologyVersion before = readyAffinityVersion();

      for(int i = 0; i < 20; i++) {
         IgniteSessionRepository.IgniteSession session = repository.createSession();
         session.setAttribute("a", "value-" + i);

         if(i % 2 == 0) {
            repository.save(session);
            repository.invalidateSession(session.getId());
         }
         else {
            repository.deleteById(session.getId());
         }

         IgniteSessionRepository.purgeSessionAttributes(session.getId());
      }

      repository.sweepSessionAttributes();

      assertEquals(before, readyAffinityVersion(),
                   "a session lifecycle must not cause a partition map exchange");
   }

   /**
    * A session's attribute names come from a scan of its own partition only: no other session's
    * attribute is listed, and the values read are those of the session and the few other
    * entries in its partition. Ignite does not store an Externalizable value (such as
    * SRPrincipal) in binary form, so a scan deserializes the ones it visits; a scan of the whole
    * cache for every session would read sessions x sessions values here.
    */
   @Test
   void attributeNames_doNotListOrDeserializeOtherSessionsAttributes() {
      List<IgniteSessionRepository.IgniteSession> sessions = new ArrayList<>();

      for(int i = 0; i < 50; i++) {
         IgniteSessionRepository.IgniteSession session = repository.createSession();
         session.setAttribute("counted", new CountingValue(i));
         session.setAttribute("own-" + i, "value");
         sessions.add(session);
      }

      CountingValue.READS.set(0);

      for(int i = 0; i < sessions.size(); i++) {
         assertEquals(Set.of("counted", "own-" + i), sessions.get(i).getAttributeNames());
      }

      assertTrue(CountingValue.READS.get() <= 2 * sessions.size(),
                 "listing the names must only read the session's own partition, but " +
                 CountingValue.READS.get() + " values were read");
      CountingValue value = sessions.get(7).getAttribute("counted");
      assertEquals(7, value.id);
   }

   /**
    * A session invalidated before its first save is not in the sessions cache, so no remove
    * event fires for it. deleteById() still schedules the purge of its attributes, and the purge
    * removes all of them, the marker included.
    */
   @Test
   void neverSavedInvalidatedSession_hasItsAttributesPurged() {
      IgniteSessionRepository.IgniteSession session = repository.createSession();
      String id = session.getId();
      session.setAttribute("a", "value");

      repository.deleteById(id);

      ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
      verify(cluster.scheduledExecutor)
         .scheduleWithId(eq("destroy-map-" + id), task.capture(), eq(10L), eq(TimeUnit.MINUTES));
      assertNotNull(IgniteSessionRepository.getSessionAttributeMap(id),
                    "the attributes are kept until the purge runs");

      task.getValue().run();

      assertNull(IgniteSessionRepository.getSessionAttributeMap(id));
      DistributedMap<SessionAttributeKey, Object> attributes = cluster.getReplicatedMap(
         IgniteSessionRepository.class.getName() + ".sessionAttributes");
      assertEquals(Set.of(), attributes.keySetByAffinityKey(id));
   }

   /**
    * An unknown session id has no attribute map.
    */
   @Test
   void unknownSessionId_hasNoAttributeMap() {
      assertNull(IgniteSessionRepository.getSessionAttributeMap("unknown-" + UUID.randomUUID()));
   }

   private static AffinityTopologyVersion readyAffinityVersion() {
      return ((IgniteEx) ignite).context().cache().context().exchange().readyAffinityVersion();
   }

   /**
    * Installs a context that returns the given cluster and delegates every other bean to the
    * class's Spring context.
    */
   @SuppressWarnings("unchecked")
   private static void installContext(Cluster cluster, ApplicationContext springContext) {
      ApplicationContext context = mock(ApplicationContext.class);
      when(context.getBean(any(Class.class)))
         .thenAnswer(inv -> springContext.getBean((Class<Object>) inv.getArgument(0)));
      doReturn(cluster).when(context).getBean(Cluster.class);
      ConfigurationContext.getContext().setApplicationContext(context);
   }

   @SuppressWarnings("unchecked")
   private static <K, V> org.apache.ignite.IgniteCache<K, V> cache(String name) {
      try {
         Method method = IgniteCluster.class.getDeclaredMethod(
            "getCacheConfiguration", String.class, CacheMode.class, int.class);
         method.setAccessible(true);
         CacheConfiguration<K, V> config =
            (CacheConfiguration<K, V>) method.invoke(null, name, CacheMode.REPLICATED, 2);
         return ignite.getOrCreateCache(config);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   /**
    * Picks distinct free ports, as the other embedded Ignite tests do, so the topology can't
    * pick up a node of another test or fork.
    */
   private static int[] freePorts(int count) {
      ServerSocketHolder holder = new ServerSocketHolder(count);
      return holder.ports;
   }

   private static final class ServerSocketHolder {
      ServerSocketHolder(int count) {
         java.net.ServerSocket[] sockets = new java.net.ServerSocket[count];
         ports = new int[count];

         try {
            for(int i = 0; i < count; i++) {
               sockets[i] = new java.net.ServerSocket(0);
               ports[i] = sockets[i].getLocalPort();
            }
         }
         catch(IOException ex) {
            throw new UncheckedIOException(ex);
         }
         finally {
            for(java.net.ServerSocket socket : sockets) {
               if(socket != null) {
                  try {
                     socket.close();
                  }
                  catch(IOException ignore) {
                  }
               }
            }
         }
      }

      private final int[] ports;
   }

   /**
    * A cluster whose replicated maps are Ignite caches with the product's configuration. The
    * sessions cache and its listener stay in memory, and the scheduled executor is a mock.
    */
   private static final class IgniteAttributesCluster extends MockCluster {
      @Override
      public <K, V> DistributedMap<K, V> getReplicatedMap(String name) {
         return new IgniteDistributedMap<>(cache(name));
      }

      @Override
      public DistributedScheduledExecutorService getScheduledExecutor() {
         return scheduledExecutor;
      }

      @Override
      public Set<String> getMapNames(String prefix) {
         Set<String> names = new HashSet<>(super.getMapNames(prefix));
         ignite.cacheNames().stream().filter(n -> n.startsWith(prefix)).forEach(names::add);
         return names;
      }

      final DistributedScheduledExecutorService scheduledExecutor =
         mock(DistributedScheduledExecutorService.class);
   }

   /**
    * A value that counts how often it is deserialized.
    */
   public static final class CountingValue implements Externalizable {
      public CountingValue() {
      }

      CountingValue(int id) {
         this.id = id;
      }

      @Override
      public void writeExternal(ObjectOutput out) throws IOException {
         out.writeInt(id);
      }

      @Override
      public void readExternal(ObjectInput in) throws IOException {
         READS.incrementAndGet();
         id = in.readInt();
      }

      static final AtomicInteger READS = new AtomicInteger();
      private int id;
   }

   private static Ignite ignite;
   private ApplicationContext savedAppContext;
   private IgniteAttributesCluster cluster;
   private IgniteSessionRepository repository;
}
