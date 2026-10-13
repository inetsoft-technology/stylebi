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

import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.internal.cluster.DistributedReference;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.*;
import org.apache.ignite.events.EventType;
import org.apache.ignite.failure.NoOpFailureHandler;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78451: an Ignite lock, semaphore, latch or atomic that a client node creates or obtains
 * registers Ignite's internal synchronous data-structure continuous query on every server. A
 * cloud-runner task container is a client started with {@code forceClientToServerConnections},
 * which a server cannot dial. After such a client is killed, and until discovery drops it, every
 * lock-state commit on a server notified the dead client on a {@code sys-stripe} thread and
 * waited about three times {@code failureDetectionTimeout} for it, so a login or logout (which
 * takes the license lock) hung for about 30 s.
 * <p>
 * Two embedded servers, a live force-mode client and a force-mode client that used the cluster
 * locks and atomics through {@link IgniteCluster} and is then killed (discovery and communication
 * SPIs failed, as a killed container's are). The real {@code IgniteCluster} methods run on bare
 * instances whose {@code ignite} is a server or a client.
 */
@Tag("slow")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IgniteClusterClientLockFailureTest {
   @BeforeAll
   void startCluster(@TempDir Path workDir) throws Exception {
      this.workDir = workDir;
      serverA = startNode("a", 0, false);
      serverB = startNode("b", 1, false);
      deadClient = startNode("c", 2, true);
      liveClient = startNode("d", 3, true);

      // the servers create the locks and atomics as they do at start-up
      for(Ignite server : List.of(serverA, serverB)) {
         IgniteCluster cluster = newBareCluster(server);
         lockAndUnlock(cluster.getLock(SHARED_LOCK));
         lockAndUnlock(cluster.getLock("other." + server.name()));
         cluster.getLong(SERVER_LONG).incrementAndGet();
      }

      // the client uses them as a task container does: Plugins creates a lock in its constructor,
      // BlobStorage takes read/write locks, the license lock is locked and unlocked
      IgniteCluster client = newBareCluster(deadClient);
      client.getLock("created.only");
      lockAndUnlock(client.getLock(SHARED_LOCK));
      client.lockRead("indexedStorage");
      client.unlockRead("indexedStorage");
      client.lockWrite("indexedStorage");
      client.unlockWrite("indexedStorage");
      client.lockKey("client.key");
      client.unlockKey("client.key");
      client.getLong(SERVER_LONG).incrementAndGet();
      client.getLong("client.long").incrementAndGet();
      client.getReference("client.reference").set("value");

      deadClientId = deadClient.cluster().localNode().id();
      ((TcpDiscoverySpi) deadClient.configuration().getDiscoverySpi()).simulateNodeFailure();
      ((TcpCommunicationSpi) deadClient.configuration().getCommunicationSpi()).simulateNodeFailure();
   }

   @AfterAll
   void stopCluster() throws InterruptedException {
      // Stopping a node whose SPIs were failed sometimes waits forever for its own Ignite workers
      // (also seen with IgniteClusterBroadcastClientFailureTest). Don't let it hold up the rest.
      Thread stopper = new Thread(() -> {
         try {
            deadClient.close();
         }
         catch(Throwable ignore) {
         }
      }, "bug78451-stop-dead-client");
      stopper.setDaemon(true);
      stopper.start();
      stopper.join(DEAD_CLIENT_STOP_MILLIS);
      nodes.remove(deadClient);

      List<Ignite> reversed = new ArrayList<>(nodes);
      Collections.reverse(reversed);

      for(Ignite node : reversed) {
         try {
            node.close();
         }
         catch(Throwable ignore) {
         }
      }
   }

   @Test
   @Order(2)
   void serverLocksDoNotStallOnDeadClient() throws Exception {
      assertDeadClientInTopology();
      ExecutorService executor = Executors.newFixedThreadPool(4);

      try {
         List<Future<Long>> futures = new ArrayList<>();

         for(Ignite server : List.of(serverA, serverB)) {
            for(String name : List.of(SHARED_LOCK, "other." + server.name())) {
               futures.add(executor.submit(() -> {
                  IgniteCluster cluster = newBareCluster(server);
                  long max = 0;

                  for(int i = 0; i < 3; i++) {
                     long start = System.nanoTime();
                     lockAndUnlock(cluster.getLock(name));
                     max = Math.max(max, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                  }

                  return max;
               }));
            }
         }

         for(Future<Long> future : futures) {
            long elapsed;

            try {
               elapsed = future.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
            catch(TimeoutException e) {
               elapsed = TEST_TIMEOUT_MILLIS;
            }

            assertTrue(elapsed < LIMIT_MILLIS,
                       "a server lock/unlock blocked for " + elapsed + " ms on the dead client");
         }
      }
      finally {
         // a lock call blocked on the dead client returns once discovery drops the client
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(CLIENT_FAILURE_DETECTION_TIMEOUT + TEST_TIMEOUT_MILLIS,
                                              TimeUnit.MILLISECONDS));
      }

      assertDeadClientInTopology();
   }

   @Test
   @Order(1)
   void serverAtomicRemovalDoesNotStallOnDeadClient() throws Exception {
      assertDeadClientInTopology();
      IgniteCluster cluster = newBareCluster(serverA);
      long start = System.nanoTime();
      cluster.destroyLong(SERVER_LONG);
      long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertTrue(elapsed < LIMIT_MILLIS,
                 "a server atomic removal blocked for " + elapsed + " ms on the dead client");
      assertDeadClientInTopology();
   }

   @Test
   void clientLockExcludesServersAndIsReentrant() throws Exception {
      IgniteCluster client = newBareCluster(liveClient);
      IgniteCluster server = newBareCluster(serverB);
      Lock clientLock = client.getLock("live.exclusive");
      clientLock.lock();

      try {
         clientLock.lock();
         clientLock.unlock();
         assertFalse(callOnOtherThread(() -> server.getLock("live.exclusive").tryLock()),
                     "a server took a lock the client holds");
      }
      finally {
         clientLock.unlock();
      }

      assertThrows(IllegalMonitorStateException.class, clientLock::unlock);

      Lock serverLock = server.getLock("live.exclusive");
      assertTrue(serverLock.tryLock(), "the client did not release the lock");

      try {
         assertFalse(callOnOtherThread(() -> client.getLock("live.exclusive")
                        .tryLock(200, TimeUnit.MILLISECONDS)),
                     "the client took a lock a server holds");
      }
      finally {
         serverLock.unlock();
      }

      assertTrue(callOnOtherThread(() -> {
         Lock lock = client.getLock("live.exclusive");

         if(lock.tryLock(5, TimeUnit.SECONDS)) {
            lock.unlock();
            return true;
         }

         return false;
      }), "the client could not take the released lock");
   }

   @Test
   void clientLockIsPerThread() throws Exception {
      IgniteCluster client = newBareCluster(liveClient);
      Lock lock = client.getLock("live.thread");
      lock.lock();

      try {
         assertFalse(callOnOtherThread(() -> client.getLock("live.thread").tryLock()),
                     "another thread of the client took a lock this thread holds");
         assertThrows(ExecutionException.class,
                      () -> callOnOtherThread(() -> {
                         client.unlockKey("live.thread");
                         return null;
                      }), "another thread of the client released a lock this thread holds");
      }
      finally {
         lock.unlock();
      }
   }

   @Test
   void clientReadWriteLocksExcludeServers() throws Exception {
      IgniteCluster client = newBareCluster(liveClient);
      IgniteCluster server = newBareCluster(serverA);
      client.lockRead("live.rw");
      CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
         server.lockWrite("live.rw");
         server.unlockWrite("live.rw");
      });

      try {
         Thread.sleep(500);
         assertFalse(writer.isDone(), "a server took the write lock while the client reads");
      }
      finally {
         client.unlockRead("live.rw");
      }

      writer.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
   }

   @Test
   void clientAtomicsAreSharedWithServers() {
      IgniteCluster client = newBareCluster(liveClient);
      IgniteCluster server = newBareCluster(serverB);
      DistributedLong serverLong = server.getLong("live.long");
      DistributedLong clientLong = client.getLong("live.long");
      serverLong.set(5);
      assertEquals(6, clientLong.incrementAndGet());
      assertTrue(clientLong.compareAndSet(6, 10));
      assertFalse(clientLong.compareAndSet(6, 11));
      assertEquals(10, serverLong.get());
      assertEquals(10, clientLong.getAndAdd(-3));
      assertEquals(7, serverLong.get());

      DistributedReference<String> clientReference = client.getReference("live.reference");
      clientReference.set("a");
      assertEquals("a", server.<String>getReference("live.reference").get());
      assertTrue(clientReference.compareAndSet("a", "b"));
      assertEquals("b", clientReference.getAndSet("c"));
      assertEquals("c", server.<String>getReference("live.reference").get());

      client.destroyLong("live.long");
      assertEquals(0, server.getLong("live.long").get());
   }

   @Test
   void clientLocksAreReleasedWhenTheClientLeaves() throws Exception {
      Ignite leaving = startNode("e", 4, true);
      IgniteCluster client = newBareCluster(leaving);
      client.getLock("leaving.lock").lock();
      client.lockKey("leaving.key");
      leaving.close();
      nodes.remove(leaving);

      IgniteCluster server = newBareCluster(serverA);

      for(String name : List.of("leaving.lock", "leaving.key")) {
         Lock lock = server.getLock(name);
         assertTrue(lock.tryLock(10, TimeUnit.SECONDS), name + " was not released");
         lock.unlock();
      }
   }

   /**
    * More client threads wait for a lock than the server has threads for waiting steps. The
    * holder's release must not queue behind them, and the lock must pass to a waiter well within
    * one wait step.
    */
   @Test
   void clientReleaseDoesNotQueueBehindClientWaits() throws Exception {
      IgniteCluster client = newBareCluster(liveClient);
      int waiters = ClientDataStructures.WAIT_EXECUTOR_SIZE + 8;
      Lock lock = client.getLock("live.contended");
      lock.lock();
      ExecutorService executor = Executors.newFixedThreadPool(waiters);
      List<Long> acquired = Collections.synchronizedList(new ArrayList<>());

      try {
         CountDownLatch started = new CountDownLatch(waiters);
         List<Future<?>> futures = new ArrayList<>();

         for(int i = 0; i < waiters; i++) {
            futures.add(executor.submit(() -> {
               Lock waiter = client.getLock("live.contended");
               started.countDown();
               waiter.lock();
               acquired.add(System.nanoTime());
               waiter.unlock();
               return null;
            }));
         }

         assertTrue(started.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
         // let every waiter send its step, so the wait executor is full
         Thread.sleep(1000);
         long start = System.nanoTime();
         lock.unlock();
         long released = System.nanoTime();
         assertTrue(TimeUnit.NANOSECONDS.toMillis(released - start) < LIMIT_MILLIS,
                    "the release took " + TimeUnit.NANOSECONDS.toMillis(released - start) +
                    " ms behind the waiting steps");

         for(Future<?> future : futures) {
            future.get(2 * TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
         }

         long first = acquired.stream().mapToLong(Long::longValue).min().orElseThrow();
         assertTrue(TimeUnit.NANOSECONDS.toMillis(first - released) < LIMIT_MILLIS,
                    "the lock passed to a waiter after " +
                    TimeUnit.NANOSECONDS.toMillis(first - released) + " ms");
         assertEquals(waiters, acquired.size());
      }
      finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
      }
   }

   /**
    * The server that granted a client's lock leaves while the client holds it, as in a rolling
    * update of the oldest server. Ignite breaks the lock, and the client's release must not
    * throw. Runs last, it stops a server.
    */
   @Test
   @Order(Integer.MAX_VALUE)
   void clientReleaseAfterGrantingServerLeftDoesNotThrow() throws Exception {
      IgniteCluster client = newBareCluster(liveClient);
      Lock lock = client.getLock("live.rolling");
      lock.lock();
      lock.lock();
      serverA.close();
      nodes.remove(serverA);

      assertDoesNotThrow(lock::unlock);
      assertDoesNotThrow(lock::unlock);
      assertThrows(IllegalMonitorStateException.class, lock::unlock);

      // the client's other locks now go to the remaining server
      Lock other = client.getLock("live.after.rolling");
      assertTrue(other.tryLock(5, TimeUnit.SECONDS));
      assertFalse(callOnOtherThread(() -> newBareCluster(serverB).getLock("live.after.rolling")
                                     .tryLock()),
                  "a server took a lock the client holds");
      other.unlock();
   }

   private void assertDeadClientInTopology() {
      assertNotNull(serverA.cluster().node(deadClientId),
                    "the dead client must still be in the topology for this test");
   }

   private static void lockAndUnlock(Lock lock) {
      lock.lock();
      lock.unlock();
   }

   private static <T> T callOnOtherThread(Callable<T> task) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         return executor.submit(task).get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
      }
      finally {
         executor.shutdownNow();
         assertTrue(executor.awaitTermination(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
      }
   }

   private static IgniteCluster newBareCluster(Ignite ignite) {
      IgniteCluster cluster = mock(IgniteCluster.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

      try {
         Field field = IgniteCluster.class.getDeclaredField("ignite");
         field.setAccessible(true);
         field.set(cluster, ignite);
         field = IgniteCluster.class.getDeclaredField("lockInfos");
         field.setAccessible(true);
         field.set(cluster, new ConcurrentHashMap<>());
      }
      catch(ReflectiveOperationException e) {
         throw new IllegalStateException(e);
      }

      return cluster;
   }

   private Ignite startNode(String name, int index, boolean client) {
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("bug78451-" + name);
      config.setConsistentId("bug78451-" + name);
      config.setWorkDirectory(workDir.resolve(name).toString());
      config.setFailureDetectionTimeout(FAILURE_DETECTION_TIMEOUT);
      // keeps the dead client in the topology for the whole test, as discovery does for up to
      // 30 s in production
      config.setClientFailureDetectionTimeout(CLIENT_FAILURE_DETECTION_TIMEOUT);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(true);
      config.setClientMode(client);
      config.setFailureHandler(new NoOpFailureHandler());
      config.setIncludeEventTypes(EventType.EVT_NODE_LEFT, EventType.EVT_NODE_FAILED);
      ClientDataStructures.addExecutors(config);
      IgniteUtils.configBinaryTypes(config);

      // as IgniteCluster.getDefaultConfig() sets with two servers
      AtomicConfiguration atomics = new AtomicConfiguration();
      atomics.setBackups(2);
      atomics.setCacheMode(CacheMode.REPLICATED);
      config.setAtomicConfiguration(atomics);

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
      communication.setLocalPort(PORTS[NODE_COUNT + index]);
      communication.setLocalPortRange(0);
      // as IgniteCluster.getDefaultConfig() sets for a cloud-runner task container
      communication.setForceClientToServerConnections(client);
      config.setCommunicationSpi(communication);

      if(!client) {
         DataStorageConfiguration storage = new DataStorageConfiguration();
         storage.getDefaultDataRegionConfiguration()
            .setInitialSize(64L << 20)
            .setMaxSize(64L << 20);
         config.setDataStorageConfiguration(storage);
         // IgniteCluster creates this cache on every server
         Ignite node = Ignition.start(config);
         node.getOrCreateCache(rwMapConfiguration());
         nodes.add(node);
         return node;
      }

      Ignite node = Ignition.start(config);
      nodes.add(node);
      return node;
   }

   private static CacheConfiguration<Object, Object> rwMapConfiguration() {
      try {
         Field field = IgniteCluster.class.getDeclaredField("RW_MAP_NAME");
         field.setAccessible(true);
         Method method = IgniteCluster.class.getDeclaredMethod("getCacheConfiguration", String.class);
         method.setAccessible(true);
         @SuppressWarnings("unchecked")
         CacheConfiguration<Object, Object> config =
            (CacheConfiguration<Object, Object>) method.invoke(null, field.get(null));
         return config;
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
      java.net.ServerSocket[] sockets = new java.net.ServerSocket[count];
      int[] ports = new int[count];

      try {
         for(int i = 0; i < count; i++) {
            sockets[i] = new java.net.ServerSocket(0);
            ports[i] = sockets[i].getLocalPort();
         }

         return ports;
      }
      catch(java.io.IOException ex) {
         throw new java.io.UncheckedIOException(ex);
      }
      finally {
         for(java.net.ServerSocket socket : sockets) {
            if(socket != null) {
               try {
                  socket.close();
               }
               catch(java.io.IOException ignore) {
               }
            }
         }
      }
   }

   private Path workDir;
   private Ignite serverA;
   private Ignite serverB;
   private Ignite deadClient;
   private Ignite liveClient;
   private UUID deadClientId;
   private final List<Ignite> nodes = new CopyOnWriteArrayList<>();

   private static final String SHARED_LOCK = "inetsoft.sree.web.ConcurrentSessionClusterService.lock";
   private static final String SERVER_LONG = "server.long";
   private static final long FAILURE_DETECTION_TIMEOUT = 3000;
   private static final long CLIENT_FAILURE_DETECTION_TIMEOUT = 60_000;
   // well under FAILURE_DETECTION_TIMEOUT, the least a notification to the dead client blocks
   private static final long LIMIT_MILLIS = 1500;
   private static final long TEST_TIMEOUT_MILLIS = 30_000;
   private static final long DEAD_CLIENT_STOP_MILLIS = 30_000;
   private static final int NODE_COUNT = 5;
   // the discovery ports of the nodes, then their communication ports
   private static final int[] PORTS = freePorts(NODE_COUNT * 2);
}
