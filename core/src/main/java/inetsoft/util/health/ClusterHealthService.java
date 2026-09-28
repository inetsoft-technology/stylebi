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
import inetsoft.util.ConfigurationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * {@code ClusterHealthService} provides health status for the cluster.
 */
@Service
@Lazy
public class ClusterHealthService {
   public ClusterHealthService(Cluster cluster, KeyValueStorageManager keyValueStorageManager) {
      this.cluster = cluster;
      this.keyValueStorageManager = keyValueStorageManager;
   }

   /**
    * Gets the cluster health status.
    *
    * @return the cluster health status.
    */
   public ClusterHealthStatus getStatus() {
      try {
         if(cluster == null) {
            return new ClusterHealthStatus(false, "Cluster not initialized");
         }

         if(!cluster.isClusterReady()) {
            return new ClusterHealthStatus(false, "Cluster topology not ready");
         }

         // Check if sreeProperties data is loaded
         if(!isSreePropertiesLoaded(cluster)) {
            return new ClusterHealthStatus(false, "Sree properties not loaded");
         }

         return new ClusterHealthStatus(true, "Cluster is ready");
      }
      catch(Exception e) {
         LOG.warn("Failed to check cluster health", e);
         return new ClusterHealthStatus(false, "Error checking cluster: " + e.getMessage());
      }
   }

   /**
    * Checks if the sreeProperties KeyValueStorage has been loaded.
    * This first verifies that the distributed map is accessible and can be read from (if the
    * cluster topology is not ready, these operations will fail), then confirms the store's
    * initial load from the backend actually completed. The map-reachability check alone is not
    * sufficient: {@code Cluster.getReplicatedMap()} creates the underlying Ignite cache on
    * demand, so it can succeed even before the load has started, let alone while it is stalled
    * (see {@link inetsoft.storage.LocalKeyValueStorage}) — hence the additional
    * {@link KeyValueStorage#isLoaded()} check below.
    */
   private boolean isSreePropertiesLoaded(Cluster cluster) {
      try {
         // The map name for sreeProperties is "inetsoft.storage.kv.sreeProperties"
         DistributedMap<String, String> map = cluster.getReplicatedMap(SREE_PROPERTIES_MAP);

         // Verify we can actually perform operations on the map
         // This will fail with NPE on AffinityTopologyVersion if topology is not ready
         int size = map.size();

         // Also try to read a key (even if it doesn't exist) to verify read operations work
         // This is a more thorough check than just getting the size
         map.containsKey("__health_check__");

         LOG.debug("sreeProperties map accessible, size: {}", size);
      }
      catch(NullPointerException e) {
         // Likely AffinityTopologyVersion is null - topology not ready
         LOG.debug("sreeProperties map not accessible - topology not ready: {}", e.getMessage());
         return false;
      }
      catch(Exception e) {
         LOG.debug("Failed to access sreeProperties map: {}", e.getMessage());
         return false;
      }

      // Reachability alone doesn't prove the store finished loading. Peek at the cached
      // storage instance (never triggers a fresh load) and require its initial load to have
      // actually completed; a store that hasn't been requested yet, or whose load timed out,
      // correctly reports not-loaded here instead of a false-positive "ready". A store whose
      // load timed out is not stuck reporting not-loaded forever: PropertiesEngine's own
      // accessor retries the load once the next time the property store is actually read or
      // written (KeyValueStorage.retryLoad(), Bug #76975), so this check starts reporting ready
      // again once that ordinary access succeeds, without requiring a restart.
      KeyValueStorage<?> storage = keyValueStorageManager.peekStorage(SREE_PROPERTIES_STORE_ID);

      if(storage == null || !storage.isLoaded()) {
         LOG.debug("sreeProperties key-value storage has not finished its initial load yet");
         return false;
      }

      return true;
   }

   private final Cluster cluster;
   private final KeyValueStorageManager keyValueStorageManager;
   private static final String SREE_PROPERTIES_MAP = "inetsoft.storage.kv.sreeProperties";
   private static final String SREE_PROPERTIES_STORE_ID = "sreeProperties";
   private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthService.class);
}
