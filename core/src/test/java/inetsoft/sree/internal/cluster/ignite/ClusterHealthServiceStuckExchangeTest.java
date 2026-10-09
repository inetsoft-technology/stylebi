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

import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.util.health.ClusterHealthService;
import inetsoft.util.health.ClusterHealthStatus;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Bug #77880: while an Ignite partition map exchange cannot finish, {@code IgniteCache.size()}
 * and {@code containsKey()} park their thread with no timeout. The cluster health check made
 * those calls on the health request thread, so every readiness probe stranded one management
 * connector thread until the exchange finished. Against a real embedded node with a stuck
 * exchange, {@link ClusterHealthService#getStatus()} must answer not-ready within its bound on
 * every call, keep at most one thread parked in the cluster, and report ready again once the
 * exchange completes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ClusterHealthServiceStuckExchangeTest {
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
      config.setIgniteInstanceName("bug77880-" + UUID.randomUUID());
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

   @Test
   void stuckExchangeReportsNotReadyWithinBoundAndRecovers() throws Exception {
      Ignite ignite = cluster.getIgniteInstance();
      DistributedMap<String, String> sreeMap = cluster.getReplicatedMap(SREE_PROPERTIES_MAP);
      sreeMap.put("key", "value");

      KeyValueStorageManager kvm = mock(KeyValueStorageManager.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(kvm).peekStorage(eq("sreeProperties"));

      ClusterHealthService service = new ClusterHealthService(cluster, kvm);
      ExecutorService requests = Executors.newCachedThreadPool(daemon("health-request"));
      CountDownLatch txLocked = new CountDownLatch(1);
      CountDownLatch releaseTx = new CountDownLatch(1);
      Thread txThread = null;
      Thread exchangeThread = null;

      try {
         assertTrue(service.getStatus().isReady(), "ready before the exchange is stuck");

         // thread A holds a pessimistic transaction lock, so a new exchange waits for it in
         // waitPartitionRelease
         IgniteCache<String, String> txCache = ignite.getOrCreateCache(
            new CacheConfiguration<String, String>("bug77880-tx")
               .setCacheMode(CacheMode.REPLICATED)
               .setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL));
         txThread = new Thread(() -> {
            try(Transaction tx = ignite.transactions().txStart(
               TransactionConcurrency.PESSIMISTIC, TransactionIsolation.REPEATABLE_READ))
            {
               txCache.put("locked", "value");
               txLocked.countDown();
               releaseTx.await();
               tx.commit();
            }
            catch(InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }, "bug77880-tx");
         txThread.setDaemon(true);
         txThread.start();
         assertTrue(txLocked.await(30, TimeUnit.SECONDS), "transaction lock taken");

         // thread B starts a cache, which starts an exchange that can't release partitions
         exchangeThread = new Thread(
            () -> ignite.getOrCreateCache("bug77880-new-" + UUID.randomUUID()),
            "bug77880-exchange");
         exchangeThread.setDaemon(true);
         exchangeThread.start();
         await().atMost(Duration.ofSeconds(30))
            .until(() -> !((IgniteEx) ignite).context().cache().context().exchange()
               .lastTopologyFuture().isDone());
         Thread.sleep(1000L);
         assertTrue(exchangeThread.isAlive(), "the exchange is stuck behind the transaction");

         // each health request answers not-ready within the bound, on its own request thread
         List<String> messages = new ArrayList<>();

         for(int i = 0; i < REQUEST_COUNT; i++) {
            Future<ClusterHealthStatus> request = requests.submit(service::getStatus);
            long start = System.nanoTime();
            ClusterHealthStatus status;

            try {
               status = request.get(BOUND_MILLIS + MARGIN_MILLIS, TimeUnit.MILLISECONDS);
            }
            catch(TimeoutException e) {
               fail("health request " + i + " did not answer within " +
                       (BOUND_MILLIS + MARGIN_MILLIS) + " ms; parked threads: " +
                       threadsParkedInHealthCheck());
               return;
            }

            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertFalse(status.isReady(), "request " + i + " reports not ready: " +
               status.getMessage() + " after " + elapsed + " ms");
            messages.add(status.getMessage());
         }

         // the first request reached the parked cluster call and timed out; the later ones
         // found that probe still running instead of starting another
         assertTrue(messages.get(0).contains("timed out"), "first request: " + messages);

         for(String message : messages.subList(1, messages.size())) {
            assertTrue(message.contains("still running"), "later requests: " + messages);
         }

         List<String> parked = threadsParkedInHealthCheck();
         assertTrue(parked.size() <= 1, "at most one thread parked in the cluster: " + parked);
         assertTrue(exchangeThread.isAlive(), "the exchange was still stuck during the requests");

         // once the exchange completes, the check reports ready again
         releaseTx.countDown();
         exchangeThread.join(30000L);
         assertFalse(exchangeThread.isAlive(), "the exchange finished after the release");
         await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
            .until(() -> service.getStatus().isReady());
         assertTrue(threadsParkedInHealthCheck().isEmpty(), "no thread left parked");
      }
      finally {
         releaseTx.countDown();

         if(txThread != null) {
            txThread.join(30000L);
         }

         if(exchangeThread != null) {
            exchangeThread.join(30000L);
         }

         requests.shutdown();
         requests.awaitTermination(30, TimeUnit.SECONDS);
         service.close();
      }
   }

   /**
    * Names of the threads currently inside a health check cluster call.
    */
   private static List<String> threadsParkedInHealthCheck() {
      List<String> names = new ArrayList<>();

      for(Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
         boolean inHealthCheck = false;
         boolean inIgnite = false;

         for(StackTraceElement frame : e.getValue()) {
            if(frame.getClassName().equals(ClusterHealthService.class.getName())) {
               inHealthCheck = true;
            }
            else if(frame.getClassName().startsWith("org.apache.ignite.")) {
               inIgnite = true;
            }
         }

         if(inHealthCheck && inIgnite) {
            names.add(e.getKey().getName());
         }
      }

      return names;
   }

   private static ThreadFactory daemon(String prefix) {
      return r -> {
         Thread thread = new Thread(r, prefix + "-" + UUID.randomUUID());
         thread.setDaemon(true);
         return thread;
      };
   }

   private static int freePort() throws Exception {
      try(ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      }
   }

   private static final String SREE_PROPERTIES_MAP = "inetsoft.storage.kv.sreeProperties";
   // the default health.cluster.timeout; the first request always uses it
   private static final long BOUND_MILLIS = 5000L;
   private static final long MARGIN_MILLIS = 2000L;
   private static final int REQUEST_COUNT = 4;
   private static IgniteCluster cluster;
}
