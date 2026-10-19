/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.sree.security.db;

import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.test.*;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;

import static inetsoft.sree.security.db.DatabaseCacheTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The Database provider cache service on two real Ignite server nodes: the service runs on the
 * node Ignite picks, and every proxy call runs there. Each node has its own security chain
 * (emulated per node, see {@link NodeChainService}). The replicated maps and the atomic longs
 * come from the test's mock cluster, as the service's {@code init()} reads them through
 * {@code Cluster.getInstance()}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class DatabaseAuthenticationCacheIgniteTest {
   @BeforeEach
   void setUp() throws Exception {
      support = new DatabaseCacheTestSupport();
      support.setUp("3600000");
      CHAINS.clear();
   }

   @AfterEach
   void tearDown() {
      for(Ignite node : nodes) {
         try {
            node.close();
         }
         catch(Throwable ignore) {
         }
      }

      nodes.clear();
      CHAINS.clear();
      support.tearDown();
   }

   /**
    * Bug #78796: the host dies in the middle of a load. The instance Ignite deploys on the
    * survivor starts its own reload chain, clears the dead host's load lock, and binds the
    * provider from its own chain on the first load.
    */
   @Test
   void failoverStartsTheChainAndClearsTheLoadLock(@TempDir Path dir) throws Exception {
      String name = "ignite78796";
      String service = "DatabaseSecurity:" + name;
      Ignite a = startNode(dir, "a", 0);
      Ignite b = startNode(dir, "b", 1);
      setChain(a, support.provider(name, ALL_USERS));
      setChain(b, support.provider(name, ALL_USERS));
      deploy(a, name);
      Ignite host = host(name, a, b);
      Ignite survivor = host == a ? b : a;
      DatabaseAuthenticationCacheService proxy = proxy(survivor, name);
      proxy.connect();
      proxy.load();
      assertTrue(await(proxy::isInitialized, 10_000));

      // the host dies during a load and never releases the lock
      setClusterLong(name, "loadingCount", 1L);
      host.close();
      assertTrue(await(() -> survivor.services().service(service) != null, 20_000),
                 "the service was not redeployed");
      DatabaseAuthenticationCacheServiceImpl redeployed = survivor.services().service(service);

      // Ignite runs execute() after it registers the instance
      assertTrue(await(() -> clusterLong(name, "loadingCount") == 0L, 10_000),
                 "the lock of the dead host was not cleared");
      assertTrue(await(() -> queueSize(redeployed) == 2, 5_000),
                 "the cleanup task and the reload chain: " + queueSize(redeployed));
      assertNull(boundProvider(redeployed));

      support.exec("INSERT INTO U VALUES ('carol', 'pw-carol')");
      proxy.refresh();
      assertTrue(await(() -> cachedUsers(name).toString().contains("carol"), 10_000),
                 "a refresh through the old proxy did not reload");
      assertSame(CHAINS.get(survivor.name()).get(), boundProvider(redeployed));
   }

   private void deploy(Ignite node, String name) {
      bare(node).getSingletonService("DatabaseSecurity:" + name,
                                     DatabaseAuthenticationCacheService.class,
                                     () -> new NodeChainService(name));
   }

   private static DatabaseAuthenticationCacheService proxy(Ignite node, String name) {
      return bare(node).getSingletonService("DatabaseSecurity:" + name,
                                            DatabaseAuthenticationCacheService.class, () -> null);
   }

   private static Ignite host(String name, Ignite... nodes) throws Exception {
      String service = "DatabaseSecurity:" + name;
      assertTrue(await(() -> Arrays.stream(nodes).anyMatch(n -> n.services().service(service) != null),
                       10_000), "the service was not deployed");
      return Arrays.stream(nodes).filter(n -> n.services().service(service) != null)
         .findFirst().orElseThrow();
   }

   static void setChain(Ignite node, DatabaseAuthenticationProvider provider) {
      CHAINS.computeIfAbsent(node.name(), k -> new AtomicReference<>()).set(provider);
   }

   static int queueSize(DatabaseAuthenticationCacheServiceImpl service) {
      try {
         Field field = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredField("executor");
         field.setAccessible(true);
         return ((ScheduledThreadPoolExecutor) field.get(service)).getQueue().size();
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }
   }

   static Object boundProvider(DatabaseAuthenticationCacheServiceImpl service) throws Exception {
      Field field = DatabaseAuthenticationCacheServiceImpl.class.getDeclaredField("provider");
      field.setAccessible(true);
      return field.get(service);
   }

   /**
    * The cache service with a per-node security chain: the provider lookup that runs on the
    * host reads the chain of the Ignite node the instance is deployed on.
    */
   static final class NodeChainService extends DatabaseAuthenticationCacheServiceImpl {
      NodeChainService(String providerName) {
         super(providerName);
         this.name = providerName;
      }

      @Override
      DatabaseAuthenticationProvider lookupProvider() {
         AtomicReference<DatabaseAuthenticationProvider> chain = CHAINS.get(ignite.name());
         DatabaseAuthenticationProvider p = chain == null ? null : chain.get();

         if(p == null) {
            throw new IllegalStateException(
               "Security provider:" + name + " not found on " + ignite.name());
         }

         return p;
      }

      private final String name;
      @IgniteInstanceResource
      private transient Ignite ignite;
   }

   private static IgniteCluster bare(Ignite ignite) {
      IgniteCluster cluster = mock(IgniteCluster.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

      try {
         Field field = IgniteCluster.class.getDeclaredField("ignite");
         field.setAccessible(true);
         field.set(cluster, ignite);
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }

      return cluster;
   }

   Ignite startNode(Path workDir, String name, int index) throws Exception {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("dbcache-" + name);
      config.setConsistentId("dbcache-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setFailureDetectionTimeout(3000);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      config.setFailureHandler(new NoOpFailureHandler());
      Class<?> cds = Class.forName("inetsoft.sree.internal.cluster.ignite.ClientDataStructures");
      Method add = cds.getDeclaredMethod("addExecutors", IgniteConfiguration.class);
      add.setAccessible(true);
      add.invoke(null, config);
      IgniteUtils.configBinaryTypes(config);

      TcpDiscoverySpi discovery = new TcpDiscoverySpi();
      discovery.setLocalAddress("127.0.0.1");
      discovery.setLocalPort(PORTS[index]);
      discovery.setLocalPortRange(0);
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(List.of("127.0.0.1:" + PORTS[0], "127.0.0.1:" + PORTS[1]));
      discovery.setIpFinder(ipFinder);
      config.setDiscoverySpi(discovery);

      TcpCommunicationSpi communication = new TcpCommunicationSpi();
      communication.setLocalAddress("127.0.0.1");
      communication.setLocalPort(PORTS[2 + index]);
      communication.setLocalPortRange(0);
      config.setCommunicationSpi(communication);

      DataStorageConfiguration storage = new DataStorageConfiguration();
      storage.getDefaultDataRegionConfiguration().setInitialSize(64L << 20).setMaxSize(64L << 20);
      config.setDataStorageConfiguration(storage);
      Ignite node = Ignition.start(config);
      nodes.add(node);
      return node;
   }

   private static int[] freePorts(int count) {
      ServerSocket[] sockets = new ServerSocket[count];
      int[] ports = new int[count];

      try {
         for(int i = 0; i < count; i++) {
            sockets[i] = new ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
         }

         return ports;
      }
      catch(IOException e) {
         throw new UncheckedIOException(e);
      }
      finally {
         for(ServerSocket socket : sockets) {
            try {
               if(socket != null) {
                  socket.close();
               }
            }
            catch(IOException ignore) {
            }
         }
      }
   }

   static final Map<String, AtomicReference<DatabaseAuthenticationProvider>> CHAINS =
      new ConcurrentHashMap<>();
   private static final int[] PORTS = freePorts(4);
   private final List<Ignite> nodes = new ArrayList<>();
   private DatabaseCacheTestSupport support;
}
