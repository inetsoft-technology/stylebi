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

package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageEvent;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobStorageManager;
import inetsoft.storage.BlobTransaction;
import inetsoft.uql.XTable;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.ThreadContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import java.io.*;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

/**
 * Cross-node persistent cache for serialized {@link TableLens} objects backed by
 * {@link BlobStorage}. Operates in prestop mode: {@link #put} is a no-op during normal operation;
 * entries are only written to the distributed store on node shutdown or when a new node broadcasts
 * a {@link TableCacheReplicationRequest}. This eliminates per-query write overhead during stable
 * cluster operation; cache misses fall back to re-execution.
 */
public class DistributedTableCacheStore implements MessageListener, SmartLifecycle {
   /**
    * Get the distributed table cache store instance
    */
   public static DistributedTableCacheStore getInstance() {
      return ConfigurationContext.getContext().getSpringBean(DistributedTableCacheStore.class);
   }

   public DistributedTableCacheStore(Cluster cluster, BlobStorageManager blobStorageManager,
                                     ObjectProvider<AssetDataCache> assetDataCacheProvider)
   {
      this.cluster = cluster;
      this.blobStorageManager = blobStorageManager;
      this.assetDataCacheProvider = assetDataCacheProvider;
      clusterId = cluster.getId();
      storages = new ConcurrentHashMap<>();

      cluster.addMessageListener(this);

      // scheduleAtFixedRate is idempotent across the cluster (deduplicates by class name),
      // so calling it on every construction is safe and ensures the task is re-registered
      // whenever the distributed executor service is re-deployed after a node restart.
      cluster.getScheduledExecutor().scheduleAtFixedRate(
         new CleanupTableCacheTask(), 1L, CLEANUP_FREQUENCY_TIME,
         TimeUnit.MINUTES);

   }

   /**
    * On startup, broadcast a replication request so that peer nodes populate the distributed store
    * with their local cache entries. The new node picks them up on cache miss; misses that arrive
    * before replication completes fall back to re-execution.
    */
   @Override
   public void start() {
      running = true;

      if(cluster.getServerClusterNodes().size() <= 1) {
         LOG.debug("DistributedTableCacheStore: skipping replication request, single-node cluster");
         return;
      }

      LOG.info("DistributedTableCacheStore: broadcasting replication request to cluster peers");

      try {
         cluster.sendMessage(new TableCacheReplicationRequest());
      }
      catch(Exception e) {
         LOG.warn("DistributedTableCacheStore: failed to broadcast replication request on startup", e);
      }
   }

   /**
    * Called by Spring before any {@code @PreDestroy} methods, guaranteeing that the local cache
    * is still populated when we flush. Uses {@code SmartLifecycle} rather than
    * {@code @PreDestroy} specifically to control ordering: {@code AssetDataCache.closeCache()}
    * (a {@code @PreDestroy}) clears the local cache, so we must flush before it runs.
    */
   @Override
   public void stop() {
      // Remove the listener first so no new replication-triggered flush threads can start.
      cluster.removeMessageListener(this);

      try {
         Thread t = asyncFlushThread.getAndSet(null);

         if(t != null && t.isAlive()) {
            // A replication-triggered flush is already in progress; wait for it instead of
            // starting a redundant flush. The join timeout matches the flush timeout so we
            // don't block the shutdown longer than intended.
            long timeoutMs = Long.parseLong(FLUSH_TIMEOUT_SECONDS.get()) * 1000L;

            try {
               t.join(timeoutMs);
            }
            catch(InterruptedException ignored) {
               Thread.currentThread().interrupt();
            }
         }
         else {
            flushLocalCache();
         }
      }
      finally {
         running = false;
      }
   }

   @Override
   public boolean isRunning() {
      return running;
   }

   @Override
   public int getPhase() {
      // High phase = stopped first among SmartLifecycle beans, and before all @PreDestroy methods.
      return Integer.MAX_VALUE - 1;
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof TableCacheReplicationRequest) {
         if(event.isLocal()) {
            return;
         }

         LOG.info("DistributedTableCacheStore: received replication request, flushing async");
         Thread t = new Thread(this::flushLocalCache, "table-cache-flush");
         t.setDaemon(true);
         asyncFlushThread.set(t);
         t.start();
      }
   }

   /**
    * Checks if the table data file exists in the blob storage.
    */
   boolean exists(DataKey dataKey) {
      if(dataKey.isLocalCacheOnly()) {
         return false;
      }

      BlobStorage<Metadata> storage = getStorage();
      return storage.exists(getKey(dataKey));
   }

   public TableLens get(DataKey dataKey, long touchTime) throws Exception {
      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();

      // don't return stale data from the store
      if(touchTime > 0 && storage.getLastModified(key).toEpochMilli() < touchTime) {
         return null;
      }

      try(InputStream storageInputStream = storage.getInputStream(key);
          GZIPInputStream gzipIn = new GZIPInputStream(storageInputStream);
          ObjectInputStream ois = new ObjectInputStream(gzipIn))
      {
         TableLens lens = (TableLens) ois.readObject();
         LOG.debug("Loaded lens {} from distributed table cache store", key);
         return lens;
      }
      catch(ZipException ex) {
         // Uncompressed entry written before this change; delete it so subsequent
         // get() calls don't repeatedly open and fail the GZIP check.
         LOG.debug("Cached lens {} is not compressed, treating as cache miss and deleting stale entry", key);

         try {
            storage.delete(key);
            storage.destroyPathLock(key);
         }
         catch(IOException deleteEx) {
            LOG.debug("Failed to delete stale uncompressed entry {}", key, deleteEx);
         }

         return null;
      }
   }

   /**
    * Writes the given lens to the distributed store. Called during flush (node shutdown or
    * replication request); not called during normal query execution.
    */
   private void put(String key, BlobStorage<Metadata> storage, TableLens lens) throws IOException {
      try(BlobTransaction<Metadata> tx = storage.beginTransaction()) {
         try(OutputStream out = tx.newStream(key, null);
             GZIPOutputStream gzipOut = new GZIPOutputStream(out);
             ObjectOutputStream oos = new ObjectOutputStream(gzipOut))
         {
            lens.moreRows(XTable.EOT);
            oos.writeObject(lens);
         }

         tx.commit();
      }
   }

   void remove(DataKey dataKey) {
      if(dataKey.isLocalCacheOnly()) {
         return;
      }

      String key = getKey(dataKey);
      BlobStorage<Metadata> storage = getStorage();

      try {
         storage.delete(key);
         storage.destroyPathLock(key);
      }
      catch(IOException e) {
         LOG.warn("Failed to remove data from cache: {}", key, e);
      }
   }

   /**
    * Iterates all entries in the local {@link AssetDataCache} and writes them to the distributed
    * store. Entries already present in the distributed store are skipped to avoid redundant writes
    * when multiple nodes respond to the same {@link TableCacheReplicationRequest}. Stops early if
    * the configured timeout ({@code distributed.table.cache.flush.timeout.seconds}) is reached.
    */
   void flushLocalCache() {
      if(!flushing.compareAndSet(false, true)) {
         LOG.debug("DistributedTableCacheStore.flushLocalCache: flush already in progress, skipping");
         return;
      }

      try {
         doFlushLocalCache();
      }
      finally {
         flushing.set(false);
      }
   }

   private void doFlushLocalCache() {
      if(cluster.getServerClusterNodes().size() <= 1) {
         LOG.debug("DistributedTableCacheStore.flushLocalCache: skipping flush, single-node cluster");
         return;
      }

      AssetDataCache cache = assetDataCacheProvider.getObject();
      Map<DataKey, TableLens> entries = cache.getLocalEntries();

      if(entries.isEmpty()) {
         return;
      }

      long timeoutSeconds = Long.parseLong(FLUSH_TIMEOUT_SECONDS.get());
      Instant deadline = Instant.now().plusSeconds(timeoutSeconds);

      LOG.info("DistributedTableCacheStore.flushLocalCache: flushing {} entr{} (timeout {}s)",
               entries.size(), entries.size() == 1 ? "y" : "ies", timeoutSeconds);

      long startMs = System.currentTimeMillis();
      int written = 0;
      int skippedLocal = 0;
      int skippedExists = 0;
      int failed = 0;

      for(Map.Entry<DataKey, TableLens> entry : entries.entrySet()) {
         if(Instant.now().isAfter(deadline)) {
            int remaining = entries.size() - written - skippedLocal - skippedExists - failed;
            LOG.warn("DistributedTableCacheStore.flushLocalCache: timeout reached — wrote {}, {} not flushed",
                     written, remaining);
            break;
         }

         DataKey dataKey = entry.getKey();

         if(dataKey.isLocalCacheOnly()) {
            skippedLocal++;
            continue;
         }

         Principal principal = cache.getPrincipalForKey(dataKey);
         Principal oldPrincipal = ThreadContext.getContextPrincipal();

         try {
            if(principal != null) {
               ThreadContext.setContextPrincipal(principal);
            }

            if(exists(dataKey)) {
               skippedExists++;
               continue;
            }

            put(getKey(dataKey), getStorage(), entry.getValue());
            written++;
         }
         catch(Exception ex) {
            if(isClusterStopped(ex)) {
               LOG.warn("DistributedTableCacheStore.flushLocalCache: cluster stopped during flush — aborting ({} entries not flushed)",
                        entries.size() - written - skippedLocal - skippedExists - failed);
               break;
            }

            LOG.warn("DistributedTableCacheStore.flushLocalCache: failed to write entry", ex);
            failed++;
         }
         finally {
            if(principal != null) {
               ThreadContext.setContextPrincipal(oldPrincipal);
            }
         }
      }

      int notFlushed = entries.size() - written - skippedLocal - skippedExists - failed;
      LOG.info("DistributedTableCacheStore.flushLocalCache: complete in {}ms — total {}, wrote {}, skipped-local {}, skipped-exists {}, failed {}, not-flushed {}",
               System.currentTimeMillis() - startMs, entries.size(), written, skippedLocal, skippedExists, failed, notFlushed);
   }

   private static boolean isClusterStopped(Throwable ex) {
      Throwable t = ex;

      while(t != null) {
         if("IgniteIllegalStateException".equals(t.getClass().getSimpleName())) {
            return true;
         }

         t = t.getCause();
      }

      return false;
   }

   private BlobStorage<Metadata> getStorage() {
      return getStorage(getStorageId(OrganizationManager.getInstance().getCurrentOrgID()));
   }

   private BlobStorage<Metadata> getStorage(String storeID) {
      if(storages.containsKey(storeID) && !storages.get(storeID).isClosed()) {
         return storages.get(storeID);
      }
      else {
         BlobStorage<Metadata> storage = blobStorageManager.getStorage(storeID, false);
         storages.put(storeID, storage);
         return storage;
      }
   }

   private String getStorageId(String orgId) {
      return orgId.toLowerCase() + "__tableCacheStore";
   }

   private String getKey(DataKey dataKey) {
      return clusterId + "__" + DigestUtils.sha256Hex(dataKey.getValue());
   }

   private volatile boolean running = false;
   private final AtomicBoolean flushing = new AtomicBoolean(false);
   private final AtomicReference<Thread> asyncFlushThread = new AtomicReference<>();
   private final Cluster cluster;
   private final BlobStorageManager blobStorageManager;
   private final ObjectProvider<AssetDataCache> assetDataCacheProvider;
   private final String clusterId;
   private final ConcurrentHashMap<String, BlobStorage<Metadata>> storages;

   private static final long CLEANUP_FREQUENCY_TIME = 30L; // minutes
   /**
    * Maximum seconds to spend flushing the local cache on shutdown. Should be less than the ECS
    * container StopTimeout to allow the flush to complete before the container is killed.
    * Default: 90 (Fargate max StopTimeout is 120s).
    */
   private static final SreeEnv.Value FLUSH_TIMEOUT_SECONDS =
      new SreeEnv.Value("distributed.table.cache.flush.timeout.seconds", 30000, "90");
   private static final Logger LOG = LoggerFactory.getLogger(DistributedTableCacheStore.class);

   public static final class Metadata implements Serializable {
   }
}
