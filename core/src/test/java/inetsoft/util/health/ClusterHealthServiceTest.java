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
package inetsoft.util.health;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code ClusterHealthService.isSreePropertiesLoaded()} must reflect the sreeProperties
 * {@link KeyValueStorage}'s real load state, not merely whether the underlying Ignite replicated
 * map is reachable (bug #77225). Before the fix, {@code Cluster.getReplicatedMap()} creating its
 * cache on demand meant the health check could report healthy even before the store's load had
 * started, let alone while it was stalled.
 * <p>
 * {@code getStatus()} must also never block its caller on the cluster (bug #77880): the cluster
 * calls run on a single probe thread with a bounded wait, a stuck probe reports not-ready and is
 * not repeated, and the check never creates the sreeProperties map.
 */
@Tag("core")
public class ClusterHealthServiceTest {
   @AfterEach
   void tearDown() {
      // let a blocked probe finish before the next test, then stop the probe thread
      release.countDown();

      if(service != null) {
         service.close();
      }
   }

   @Test
   public void reportsNotReadyWhenStoreHasNeverBeenRequested() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      // No caller has ever requested "sreeProperties" from the manager yet.
      doReturn(null).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady(),
         "a store that was never requested must not be reported as loaded");
      assertEquals("Sree properties not loaded", status.getMessage());
   }

   @Test
   public void reportsNotReadyWhenStoreTimedOutDuringLoad() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      when(storage.isLoaded()).thenReturn(false);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady(),
         "a store whose initial load timed out must not be reported as loaded, even though " +
         "the replicated map itself is reachable");
      assertEquals("Sree properties not loaded", status.getMessage());
   }

   /**
    * Bug #78105: a node left in a smaller cluster after a split must report not-ready, even
    * though its own cluster is active and its property store is loaded.
    */
   @Test
   public void reportsNotReadyInMinorityIsland() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);
      String island = "Node is in a minority cluster island: ...";

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.getMinorityIslandMessage()).thenReturn(island);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady(), "a minority island must not be reported as ready");
      assertEquals(island, status.getMessage());
   }

   @Test
   public void reportsReadyWhenStoreLoadedNormally() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertTrue(status.isReady(),
         "the normal, successful-load case must still report ready (regression check)");
      assertEquals("Cluster is ready", status.getMessage());
      // the bounded probe still exercises the map, which is what detects startup readiness
      verify(map).size();
      verify(map).containsKey("__health_check__");
   }

   @Test
   public void blockedProbeReportsNotReadyWithinTimeoutAndIsNotRepeated() throws Exception {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);
      CountDownLatch blocked = new CountDownLatch(1);
      AtomicBoolean interrupted = new AtomicBoolean();

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      // size() parks like GridCacheAdapter.size() does during a stuck partition map exchange
      when(map.size()).thenAnswer(inv -> {
         blocked.countDown();

         try {
            release.await();
         }
         catch(InterruptedException e) {
            interrupted.set(true);
            throw e;
         }

         return 0;
      });
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, BLOCKED_TIMEOUT_MILLIS);

      long start = System.nanoTime();
      ClusterHealthStatus first = service.getStatus();
      long firstMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertTrue(blocked.await(5, TimeUnit.SECONDS), "the probe must have reached size()");
      assertFalse(first.isReady(), "a probe blocked on the cluster must report not-ready");
      assertEquals("Cluster health check timed out after " + BLOCKED_TIMEOUT_MILLIS + " ms",
                   first.getMessage());
      assertTrue(firstMillis >= BLOCKED_TIMEOUT_MILLIS - 50 &&
                 firstMillis < BLOCKED_TIMEOUT_MILLIS + 2000,
                 "getStatus() must return at the timeout, took " + firstMillis + " ms");

      start = System.nanoTime();
      ClusterHealthStatus second = service.getStatus();
      long secondMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

      assertFalse(second.isReady(), "never report ready while a probe is still running");
      assertEquals("Cluster health check still running", second.getMessage());
      assertTrue(secondMillis < BLOCKED_TIMEOUT_MILLIS,
                 "a call during a running probe must not wait, took " + secondMillis + " ms");
      // the second call must not have started another cluster operation
      verify(cluster, times(1)).isClusterReady();
      verify(map, times(1)).size();

      // once the cluster recovers, the parked probe finishes and the next call reports ready
      release.countDown();
      await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
         ClusterHealthStatus status = service.getStatus();
         assertTrue(status.isReady(), status.getMessage());
      });
      assertFalse(interrupted.get(), "a timed-out probe must not be interrupted");
   }

   @Test
   public void blockedClusterReadyCheckIsAlsoBounded() throws Exception {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      CountDownLatch blocked = new CountDownLatch(1);

      // isClusterReady() can block too, e.g. in IgniteClusterImpl.state() during a transition
      when(cluster.isClusterReady()).thenAnswer(inv -> {
         blocked.countDown();
         release.await();
         return false;
      });

      service = new ClusterHealthService(cluster, keyValueStorageManager, BLOCKED_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertTrue(blocked.await(5, TimeUnit.SECONDS));
      assertFalse(status.isReady());
      assertEquals("Cluster health check timed out after " + BLOCKED_TIMEOUT_MILLIS + " ms",
                   status.getMessage());
      verify(cluster, never()).getReplicatedMap(anyString());

      release.countDown();
      await().atMost(Duration.ofSeconds(5)).untilAsserted(
         () -> assertEquals("Cluster topology not ready", service.getStatus().getMessage()));
   }

   @Test
   public void concurrentCallWaitsForRunningHealthyProbe() throws Exception {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);
      CountDownLatch blocked = new CountDownLatch(1);

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists(anyString())).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      // a healthy but slow exchange: size() takes a while, well within the bound
      when(map.size()).thenAnswer(inv -> {
         blocked.countDown();
         release.await();
         return 1;
      });
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ExecutorService callers = Executors.newFixedThreadPool(2);

      try {
         Future<ClusterHealthStatus> first = callers.submit(service::getStatus);
         assertTrue(blocked.await(5, TimeUnit.SECONDS), "the first probe must have reached size()");
         Future<ClusterHealthStatus> second = callers.submit(service::getStatus);

         // the second call joins the running probe rather than answering not-ready at once
         assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));

         release.countDown();
         ClusterHealthStatus firstStatus = first.get(5, TimeUnit.SECONDS);
         ClusterHealthStatus secondStatus = second.get(5, TimeUnit.SECONDS);

         assertTrue(firstStatus.isReady(), firstStatus.getMessage());
         assertTrue(secondStatus.isReady(),
                    "a call during a healthy probe gets its result: " + secondStatus.getMessage());
         // both answers came from the one probe
         verify(cluster, times(1)).isClusterReady();
         verify(map, times(1)).size();
      }
      finally {
         callers.shutdownNow();
      }
   }

   @Test
   public void concurrentCallDuringStuckProbeAnswersByTheProbeDeadline() throws Exception {
      long timeout = 2000L;
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      CountDownLatch blocked = new CountDownLatch(1);

      when(cluster.isClusterReady()).thenAnswer(inv -> {
         blocked.countDown();
         release.await();
         return false;
      });

      service = new ClusterHealthService(cluster, keyValueStorageManager, timeout);
      ExecutorService callers = Executors.newFixedThreadPool(1);

      try {
         Future<ClusterHealthStatus> first = callers.submit(service::getStatus);
         assertTrue(blocked.await(5, TimeUnit.SECONDS));
         Thread.sleep(timeout / 2);

         long start = System.nanoTime();
         ClusterHealthStatus second = service.getStatus();
         long secondMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

         assertFalse(second.isReady());
         assertEquals("Cluster health check timed out after " + timeout + " ms",
                      second.getMessage());
         // it waited only for what was left of the running probe's bound, not a bound of its own
         assertTrue(secondMillis < timeout,
                    "a call joining a stuck probe must give up at that probe's deadline, took " +
                    secondMillis + " ms");
         assertFalse(first.get(5, TimeUnit.SECONDS).isReady());
         verify(cluster, times(1)).isClusterReady();
      }
      finally {
         callers.shutdownNow();
      }
   }

   @Test
   public void timeoutChangeAppliesFromTheNextProbeAndInvalidValueIsIgnored() throws Exception {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      AtomicReference<LongSupplier> source = new AtomicReference<>(() -> {
         throw new NumberFormatException("For input string: \"abc\"");
      });
      AtomicReference<CountDownLatch> gate = new AtomicReference<>();

      when(cluster.isClusterReady()).thenAnswer(inv -> {
         CountDownLatch latch = gate.get();

         if(latch != null) {
            latch.await();
         }

         return false;
      });

      service = new ClusterHealthService(cluster, keyValueStorageManager,
                                         () -> source.get().getAsLong(), BLOCKED_TIMEOUT_MILLIS);

      // an unparsable value keeps the current timeout
      assertEquals("Cluster topology not ready", service.getStatus().getMessage());
      gate.set(release);
      assertEquals("Cluster health check timed out after " + BLOCKED_TIMEOUT_MILLIS + " ms",
                   service.getStatus().getMessage());
      gate.set(null);
      release.countDown();
      await().atMost(Duration.ofSeconds(5)).untilAsserted(
         () -> assertEquals("Cluster topology not ready", service.getStatus().getMessage()));

      // a valid value read by one probe bounds the next one
      source.set(() -> 700L);
      assertEquals("Cluster topology not ready", service.getStatus().getMessage());
      CountDownLatch release2 = new CountDownLatch(1);
      gate.set(release2);

      try {
         assertEquals("Cluster health check timed out after 700 ms",
                      service.getStatus().getMessage());
      }
      finally {
         release2.countDown();
      }
   }

   @Test
   public void missingMapIsNotCreated() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);

      when(cluster.isClusterReady()).thenReturn(true);
      when(cluster.mapExists("inetsoft.storage.kv.sreeProperties")).thenReturn(false);
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady(), "a missing sreeProperties map means not loaded");
      assertEquals("Sree properties not loaded", status.getMessage());
      // getReplicatedMap() would create the cache, which is itself a partition map exchange
      verify(cluster, never()).getReplicatedMap(anyString());
   }

   @Test
   public void reportsNotReadyAfterClose() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);

      service = new ClusterHealthService(cluster, keyValueStorageManager, FAST_TIMEOUT_MILLIS);
      service.close();
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady());
      assertEquals("Cluster health check is shut down", status.getMessage());
      verifyNoInteractions(cluster);
   }

   // tests that never block use a generous bound, so a slow CI fork can't make them time out
   private static final long FAST_TIMEOUT_MILLIS = 10000L;
   // tests that block the probe wait for this bound
   private static final long BLOCKED_TIMEOUT_MILLIS = 500L;
   private final CountDownLatch release = new CountDownLatch(1);
   private ClusterHealthService service;
}
