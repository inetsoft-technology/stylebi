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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.function.LongSupplier;

/**
 * {@code ClusterHealthService} provides health status for the cluster.
 * <p>
 * Every cluster call the check makes ({@code isClusterReady()}, the map lookup, {@code size()}
 * and {@code containsKey()}) can park its thread with no timeout while an Ignite partition map
 * exchange cannot finish. The check therefore runs those calls on a single daemon probe thread
 * and waits for at most {@code health.cluster.timeout} milliseconds (default 5000). A probe that
 * overruns reports not-ready and is left running: it is never interrupted, and no new probe
 * starts until it finishes, so a stuck cluster holds one probe thread rather than one request
 * thread per health request (Bug #77880).
 */
@Service
@Lazy
public class ClusterHealthService {
   @Autowired
   public ClusterHealthService(Cluster cluster, KeyValueStorageManager keyValueStorageManager) {
      this(cluster, keyValueStorageManager, ClusterHealthService::readTimeoutProperty,
           DEFAULT_TIMEOUT_MILLIS);
   }

   /**
    * Creates the service with a fixed probe timeout, in milliseconds.
    */
   ClusterHealthService(Cluster cluster, KeyValueStorageManager keyValueStorageManager,
                        long timeoutMillis)
   {
      this(cluster, keyValueStorageManager, () -> timeoutMillis, timeoutMillis);
   }

   /**
    * @param timeoutSource the source of the probe timeout, in milliseconds. It is only called on
    *                      the probe thread, so it may read the property store without blocking a
    *                      health request.
    * @param initialTimeoutMillis the timeout used until the first probe has read the source.
    */
   private ClusterHealthService(Cluster cluster, KeyValueStorageManager keyValueStorageManager,
                                LongSupplier timeoutSource, long initialTimeoutMillis)
   {
      this.cluster = cluster;
      this.keyValueStorageManager = keyValueStorageManager;
      this.timeoutSource = timeoutSource;
      this.timeoutMillis = initialTimeoutMillis;
      this.executor = Executors.newSingleThreadExecutor(r -> {
         Thread thread = new Thread(r, "ClusterHealthProbe");
         thread.setDaemon(true);
         return thread;
      });
   }

   /**
    * Gets the cluster health status. Returns within the probe timeout, and at once when a
    * previous probe is still running.
    *
    * @return the cluster health status.
    */
   public ClusterHealthStatus getStatus() {
      if(cluster == null) {
         return new ClusterHealthStatus(false, "Cluster not initialized");
      }

      CompletableFuture<ClusterHealthStatus> probe;

      synchronized(this) {
         if(inFlight != null && !inFlight.isDone()) {
            // a previous probe is still blocked on the cluster; don't start another one and
            // never report a result from before it started
            LOG.debug("Previous cluster health probe is still running");
            return new ClusterHealthStatus(false, "Cluster health check still running");
         }

         try {
            probe = CompletableFuture.supplyAsync(this::probe, executor);
         }
         catch(RejectedExecutionException e) {
            return new ClusterHealthStatus(false, "Cluster health check is shut down");
         }

         inFlight = probe;
      }

      long timeout = timeoutMillis;

      try {
         return probe.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException e) {
         // leave the probe running: interrupting it would let the next request start another
         // cluster operation that blocks the same way
         LOG.warn("Cluster health check did not complete within {} ms", timeout);
         return new ClusterHealthStatus(
            false, "Cluster health check timed out after " + timeout + " ms");
      }
      catch(InterruptedException e) {
         Thread.currentThread().interrupt();
         return new ClusterHealthStatus(false, "Cluster health check interrupted");
      }
      catch(Exception e) {
         LOG.warn("Failed to check cluster health", e);
         return new ClusterHealthStatus(false, "Error checking cluster: " + e.getMessage());
      }
   }

   /**
    * Stops the probe thread when the application context closes.
    */
   @PreDestroy
   public void close() {
      executor.shutdownNow();
   }

   /**
    * Runs the cluster checks. Called only on the probe thread.
    */
   private ClusterHealthStatus probe() {
      refreshTimeout();

      try {
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

   private void refreshTimeout() {
      try {
         long timeout = timeoutSource.getAsLong();

         if(timeout > 0) {
            timeoutMillis = timeout;
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to read the cluster health check timeout: {}", e.getMessage());
      }
   }

   private static long readTimeoutProperty() {
      return Long.parseLong(SreeEnv.getProperty(
         TIMEOUT_PROPERTY, Long.toString(DEFAULT_TIMEOUT_MILLIS)).trim());
   }

   /**
    * Checks if the sreeProperties KeyValueStorage has been loaded.
    * This first verifies that the distributed map is accessible and can be read from (if the
    * cluster topology is not ready, these operations will fail), then confirms the store's
    * initial load from the backend actually completed. The map-reachability check alone is not
    * sufficient: the store creates the underlying Ignite cache before its load starts, so the
    * map can be reachable before the load has started, let alone while it is stalled
    * (see {@link inetsoft.storage.LocalKeyValueStorage}) — hence the additional
    * {@link KeyValueStorage#isLoaded()} check below. Runs on the probe thread only.
    */
   private boolean isSreePropertiesLoaded(Cluster cluster) {
      try {
         // Don't create the map from the health check: creating a missing cache is itself a
         // partition map exchange. LocalKeyValueStorage creates this map before the store can
         // report loaded, so a missing map just means not loaded yet.
         if(!cluster.mapExists(SREE_PROPERTIES_MAP)) {
            LOG.debug("sreeProperties map does not exist yet");
            return false;
         }

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
   private final LongSupplier timeoutSource;
   private final ExecutorService executor;
   private CompletableFuture<ClusterHealthStatus> inFlight; // guarded by this
   private volatile long timeoutMillis;
   private static final String TIMEOUT_PROPERTY = "health.cluster.timeout";
   private static final long DEFAULT_TIMEOUT_MILLIS = 5000L;
   private static final String SREE_PROPERTIES_MAP = "inetsoft.storage.kv.sreeProperties";
   private static final String SREE_PROPERTIES_STORE_ID = "sreeProperties";
   private static final Logger LOG = LoggerFactory.getLogger(ClusterHealthService.class);
}
