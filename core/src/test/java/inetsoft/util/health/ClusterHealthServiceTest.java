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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code ClusterHealthService.isSreePropertiesLoaded()} must reflect the sreeProperties
 * {@link KeyValueStorage}'s real load state, not merely whether the underlying Ignite replicated
 * map is reachable (bug #77225). Before the fix, {@code Cluster.getReplicatedMap()} creating its
 * cache on demand meant the health check could report healthy even before the store's load had
 * started, let alone while it was stalled.
 */
@Tag("core")
public class ClusterHealthServiceTest {
   @Test
   public void reportsNotReadyWhenStoreHasNeverBeenRequested() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);

      when(cluster.isClusterReady()).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      // No caller has ever requested "sreeProperties" from the manager yet.
      doReturn(null).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      ClusterHealthService service = new ClusterHealthService(cluster, keyValueStorageManager);
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
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      when(storage.isLoaded()).thenReturn(false);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      ClusterHealthService service = new ClusterHealthService(cluster, keyValueStorageManager);
      ClusterHealthStatus status = service.getStatus();

      assertFalse(status.isReady(),
         "a store whose initial load timed out must not be reported as loaded, even though " +
         "the replicated map itself is reachable");
      assertEquals("Sree properties not loaded", status.getMessage());
   }

   @Test
   public void reportsReadyWhenStoreLoadedNormally() {
      Cluster cluster = mock(Cluster.class);
      KeyValueStorageManager keyValueStorageManager = mock(KeyValueStorageManager.class);
      DistributedMap<String, String> map = mock(DistributedMap.class);
      KeyValueStorage<?> storage = mock(KeyValueStorage.class);

      when(cluster.isClusterReady()).thenReturn(true);
      doReturn(map).when(cluster).getReplicatedMap(anyString());
      when(storage.isLoaded()).thenReturn(true);
      doReturn(storage).when(keyValueStorageManager).peekStorage(eq("sreeProperties"));

      ClusterHealthService service = new ClusterHealthService(cluster, keyValueStorageManager);
      ClusterHealthStatus status = service.getStatus();

      assertTrue(status.isReady(),
         "the normal, successful-load case must still report ready (regression check)");
      assertEquals("Cluster is ready", status.getMessage());
   }
}
