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
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
import inetsoft.web.GlobalExceptionHandler;
import org.apache.ignite.internal.processors.cache.CacheStoppedException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.*;

/**
 * {@link LocalKeyValueStorage#isLoaded()} must reflect whether the constructor's initial load
 * actually finished, not merely whether the constructor returned (bug #77225). Before the fix, a
 * timed-out initial load was swallowed into a generic warning and the resulting instance was
 * indistinguishable from one that loaded successfully.
 */
@Tag("core")
public class LocalKeyValueStorageTest {
   @Test
   public void isLoadedIsFalseWhenInitialLoadTimesOut() throws Exception {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, Serializable> map = mock(DistributedMap.class);
      doReturn(map).when(cluster).getReplicatedMap(anyString());

      // Simulate LoadKeyValueTask.run() never returning within the constructor's 3-minute
      // wait: the mocked Future throws TimeoutException from get(timeout, unit) immediately,
      // instead of actually blocking for 3 real minutes.
      Future<?> timedOutLoad = mock(Future.class);
      when(timedOutLoad.get(anyLong(), any(TimeUnit.class)))
         .thenThrow(new TimeoutException("simulated slow backend"));
      doReturn(timedOutLoad).when(cluster).submit(anyString(), any(SingletonRunnableTask.class));

      LoadKeyValueTask<Serializable> load = new LoadKeyValueTask<>("test-store");
      LocalKeyValueStorage<Serializable> storage =
         new LocalKeyValueStorage<>("test-store", load, cluster);

      assertFalse(storage.isLoaded(),
         "isLoaded() must be false after the initial load times out, so callers (e.g. " +
         "ClusterHealthService) can tell it apart from a successful load");
   }

   @Test
   public void isLoadedIsTrueWhenInitialLoadCompletesNormally() throws Exception {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, Serializable> map = mock(DistributedMap.class);
      doReturn(map).when(cluster).getReplicatedMap(anyString());

      // The ordinary, fast success case: the singleton task completes well within the timeout.
      doReturn(CompletableFuture.completedFuture(null))
         .when(cluster).submit(anyString(), any(SingletonRunnableTask.class));

      LoadKeyValueTask<Serializable> load = new LoadKeyValueTask<>("test-store");
      LocalKeyValueStorage<Serializable> storage =
         new LocalKeyValueStorage<>("test-store", load, cluster);

      assertTrue(storage.isLoaded(),
         "isLoaded() must remain true for the normal, successful load case (regression check)");
   }

   /**
    * Bug #78559, a read on a store that another node deleted re-creates the cache and is retried
    * once, so it returns the (empty) content instead of failing with CacheStoppedException.
    */
   @Test
   public void readReattachesWhenCacheIsStopped() {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, String> stale = mock(DistributedMap.class);
      DistributedMap<String, String> fresh = mock(DistributedMap.class);
      doReturn(stale, fresh).when(cluster).getReplicatedMap(anyString());
      doReturn(CompletableFuture.completedFuture(null))
         .when(cluster).submit(anyString(), any(SingletonRunnableTask.class));
      when(stale.get("k")).thenThrow(cacheStopped());
      when(stale.containsKey("k")).thenThrow(cacheStopped());
      when(stale.size()).thenThrow(cacheStopped());
      when(stale.keySet()).thenThrow(cacheStopped());
      when(stale.entrySet()).thenThrow(cacheStopped());
      when(fresh.get("k")).thenReturn(null);
      when(fresh.containsKey("k")).thenReturn(false);
      when(fresh.size()).thenReturn(0);
      when(fresh.keySet()).thenReturn(Set.of());
      when(fresh.entrySet()).thenReturn(Set.of());

      LocalKeyValueStorage<String> storage = createStorage(cluster);

      assertNull(storage.get("k"));
      assertFalse(storage.contains("k"));
      assertEquals(0, storage.size());
      assertEquals(List.of(), storage.keys().toList());
      assertEquals(List.of(), storage.stream().toList());
      // reattached once, later reads use the new map
      verify(cluster, times(2)).getReplicatedMap(anyString());
      verify(stale, times(1)).get("k");
   }

   /**
    * During a node shutdown the cache cannot be created again, so the original exception must be
    * thrown and still be recognized as a CacheStoppedException (503 "System is shutting down").
    */
   @Test
   public void readRethrowsWhenCacheCannotBeCreatedAgain() {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, String> stale = mock(DistributedMap.class);
      IllegalStateException stopping = new IllegalStateException("Grid is stopping");
      doReturn(stale).doThrow(stopping).when(cluster).getReplicatedMap(anyString());
      doReturn(CompletableFuture.completedFuture(null))
         .when(cluster).submit(anyString(), any(SingletonRunnableTask.class));
      IllegalStateException stopped = cacheStopped();
      when(stale.get("k")).thenThrow(stopped);

      LocalKeyValueStorage<String> storage = createStorage(cluster);

      IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> storage.get("k"));
      assertSame(stopped, thrown);
      assertTrue(GlobalExceptionHandler.isCacheStoppedException(thrown));
      assertArrayEquals(new Throwable[] { stopping }, thrown.getSuppressed());
      verify(stale, times(1)).get("k");
   }

   /**
    * The read is retried only once, so a cache that is stopped again does not loop.
    */
   @Test
   public void readRetriesOnlyOnce() {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, String> stale = mock(DistributedMap.class);
      DistributedMap<String, String> alsoStale = mock(DistributedMap.class);
      doReturn(stale, alsoStale).when(cluster).getReplicatedMap(anyString());
      doReturn(CompletableFuture.completedFuture(null))
         .when(cluster).submit(anyString(), any(SingletonRunnableTask.class));
      when(stale.get("k")).thenThrow(cacheStopped());
      IllegalStateException second = cacheStopped();
      when(alsoStale.get("k")).thenThrow(second);

      LocalKeyValueStorage<String> storage = createStorage(cluster);

      assertSame(second, assertThrows(IllegalStateException.class, () -> storage.get("k")));
      verify(cluster, times(2)).getReplicatedMap(anyString());
      verify(alsoStale, times(1)).get("k");
   }

   /**
    * Other read failures are not treated as a deleted store.
    */
   @Test
   public void readDoesNotReattachOnOtherFailures() {
      Cluster cluster = mock(Cluster.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      doReturn(CompletableFuture.completedFuture(null))
         .when(cluster).submit(anyString(), any(SingletonRunnableTask.class));
      IllegalStateException other = new IllegalStateException("other");
      when(map.get("k")).thenThrow(other);

      LocalKeyValueStorage<String> storage = createStorage(cluster);

      assertSame(other, assertThrows(IllegalStateException.class, () -> storage.get("k")));
      verify(cluster, times(1)).getReplicatedMap(anyString());
   }

   private static LocalKeyValueStorage<String> createStorage(Cluster cluster) {
      return new LocalKeyValueStorage<>(
         "test-store", new LoadKeyValueTask<>("test-store"), cluster);
   }

   // the form Ignite's GridCacheGateway throws for an operation on a stopped cache
   private static IllegalStateException cacheStopped() {
      return new IllegalStateException(new CacheStoppedException("inetsoft.storage.kv.test-store"));
   }
}
