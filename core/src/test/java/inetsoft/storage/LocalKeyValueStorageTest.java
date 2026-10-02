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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
