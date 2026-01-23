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
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code ClusterHealthService} provides health status for the cluster.
 */
public class ClusterHealthService {
   public static ClusterHealthService getInstance() {
      return SingletonManager.getInstance(ClusterHealthService.class);
   }

   /**
    * Gets the cluster health status.
    *
    * @return the cluster health status.
    */
   public ClusterHealthStatus getStatus() {
      try {
         Cluster cluster = Cluster.getInstance();

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
    * This verifies that the distributed map is accessible and can be read from.
    * If the cluster topology is not ready, these operations will fail.
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
         return true;
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
   }

   private static final String SREE_PROPERTIES_MAP = "inetsoft.storage.kv.sreeProperties";
   private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthService.class);
}
