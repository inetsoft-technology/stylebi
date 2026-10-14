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
package inetsoft.uql.asset;

import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Bug #78463, the owners of the snapshot copies in {@link SnapshotEmbeddedTableAssembly#FILE_OWNERS_MAP}
 * are Ignite node ids, and an owner that is not in the topology of the local node was taken as
 * gone. Two JVMs that read the same cache directory (the server and the local scheduler, or a
 * cloud-runner client on a mounted cache directory) can both stay alive after a pause longer than
 * the failure detection timeout, as two clusters that never merge. Each one then sees the other
 * as gone and deletes the copies that the other still reads.
 * <p>
 * A JVM that has live snapshot tables under a node id keeps a lease file for that id in the
 * directory of the copies, and rewrites it with a new counter every {@link #REFRESH_INTERVAL}.
 * An owner that is not in the topology is gone only if its lease is missing, or its counter has
 * not changed for {@link #getTimeout()} on the clock of the reader, so the clocks of the two JVMs
 * (e.g. on two hosts sharing a network mount) are never compared. A JVM of an older version
 * writes no lease, so its owners are judged by the topology alone, as before. With a cache
 * directory per node, the lease of another node is never in the local directory, which is right
 * as that node can't read the local copies.
 * <p>
 * The name starts with {@link Tool#PERSISTENT_PREFIX}, so the cache clean-up of the Enterprise
 * Manager keeps it, and it doesn't end with ".tdat" or a number, so the data and report sweeps
 * skip it. A lease that is deleted anyway is written again by the next refresh.
 */
final class SnapshotOwnerLeases {
   private SnapshotOwnerLeases() {
   }

   /**
    * Gets the lease file of a node id in a directory.
    */
   static File getLeaseFile(File dir, String nodeId) {
      return new File(dir, LEASE_PREFIX + nodeId + LEASE_SUFFIX);
   }

   /**
    * Adds a live table of this JVM under a node id that reads copies in a directory. The lease is
    * written before this returns.
    */
   static void acquire(File dir, String nodeId) {
      if(dir == null || nodeId == null) {
         return;
      }

      File lease = getLeaseFile(dir, nodeId);

      if(LOCAL_LEASES.merge(lease.getAbsolutePath(), 1, Integer::sum) == 1) {
         write(lease);
      }

      startRefresh();
   }

   /**
    * Removes a live table of this JVM that was added by {@link #acquire}. The lease is deleted
    * when the node id has no live table in the directory anymore.
    */
   static void release(File dir, String nodeId) {
      if(dir == null || nodeId == null) {
         return;
      }

      File lease = getLeaseFile(dir, nodeId);
      String path = lease.getAbsolutePath();

      if(LOCAL_LEASES.computeIfPresent(path, (k, v) -> v <= 1 ? null : v - 1) == null) {
         synchronized(LOCAL_LEASES) {
            if(!LOCAL_LEASES.containsKey(path)) {
               lease.delete();
            }
         }
      }
   }

   /**
    * Checks if the owner of a copy that is not in the topology of this node is still alive,
    * i.e. if its lease is held by this JVM, or is in the directory of the copy and its counter
    * changed within the timeout.
    *
    * @param file   the absolute path of the copy.
    * @param nodeId the node id of the owner.
    */
   static boolean isAlive(String file, String nodeId) {
      File dir = new File(file).getParentFile();

      if(dir == null) {
         return false;
      }

      File lease = getLeaseFile(dir, nodeId);
      String path = lease.getAbsolutePath();

      if(LOCAL_LEASES.containsKey(path)) {
         return true;
      }

      String counter;

      try {
         counter = Files.readString(lease.toPath(), StandardCharsets.UTF_8).trim();
      }
      catch(IOException e) {
         // no lease, the owner is a JVM of an older version or is gone. a lease that exists but
         // can't be read now is taken as alive
         OBSERVED.remove(path);
         return lease.exists();
      }

      // being written, the owner is alive
      if(counter.isEmpty()) {
         return true;
      }

      long now = clock.getAsLong();
      Observed observed = OBSERVED.get(path);

      if(observed == null || !observed.counter().equals(counter)) {
         OBSERVED.put(path, new Observed(counter, now));
         return true;
      }

      return now - observed.since() < getTimeout();
   }

   /**
    * Deletes the lease of an owner that was found gone.
    */
   static void removeDead(String file, String nodeId) {
      File dir = new File(file).getParentFile();

      if(dir == null) {
         return;
      }

      File lease = getLeaseFile(dir, nodeId);

      if(!LOCAL_LEASES.containsKey(lease.getAbsolutePath())) {
         OBSERVED.remove(lease.getAbsolutePath());
         lease.delete();
      }
   }

   /**
    * Deletes the leases in the cache directory that have not been written for a day, e.g. of a
    * JVM that was halted after its owners were removed. A live lease is written every
    * {@link #REFRESH_INTERVAL}, so the clock of another host can't make it look that old.
    */
   static void removeOrphans() {
      File[] files;

      try {
         files = FileSystemService.getInstance().getCacheFolder().listFiles(
            (d, name) -> name.startsWith(LEASE_PREFIX) && name.endsWith(LEASE_SUFFIX));
      }
      catch(Exception e) {
         LOG.debug("Failed to list the snapshot owner leases", e);
         return;
      }

      if(files == null) {
         return;
      }

      long expired = System.currentTimeMillis() - ORPHAN_AGE;

      for(File file : files) {
         String path = file.getAbsolutePath();

         if(!LOCAL_LEASES.containsKey(path) && file.lastModified() < expired) {
            OBSERVED.remove(path);
            file.delete();
         }
      }
   }

   /**
    * Gets how long the counter of a lease must stay the same before its owner is gone. It covers
    * a pause of the owner well past the failure detection timeout of the cluster.
    */
   static long getTimeout() {
      long failureDetectionTimeout = DEFAULT_FAILURE_DETECTION_TIMEOUT;

      try {
         Long value = InetsoftConfig.getInstance().getCluster().getFailureDetectionTimeout();

         if(value != null && value > 0) {
            failureDetectionTimeout = value;
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to read the failure detection timeout", e);
      }

      return Math.max(MIN_TIMEOUT, 3 * failureDetectionTimeout);
   }

   private static void startRefresh() {
      synchronized(LOCAL_LEASES) {
         if(refresh != null) {
            return;
         }

         ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, REFRESH_THREAD);
            thread.setDaemon(true);
            return thread;
         });
         executor.scheduleWithFixedDelay(SnapshotOwnerLeases::refresh, REFRESH_INTERVAL,
                                         REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
         refresh = executor;
         Runtime.getRuntime().addShutdownHook(
            new Thread(SnapshotOwnerLeases::deleteLocal, REFRESH_THREAD + " Shutdown"));
      }
   }

   private static void refresh() {
      try {
         synchronized(LOCAL_LEASES) {
            for(String path : LOCAL_LEASES.keySet()) {
               write(new File(path));
            }
         }
      }
      catch(Throwable e) {
         LOG.debug("Failed to refresh the snapshot owner leases", e);
      }
   }

   // the leases of a halted or crashed JVM are left, and are removed when found gone
   private static void deleteLocal() {
      synchronized(LOCAL_LEASES) {
         for(String path : LOCAL_LEASES.keySet()) {
            new File(path).delete();
         }
      }
   }

   private static void write(File lease) {
      try {
         Files.writeString(lease.toPath(), Long.toString(COUNTER.incrementAndGet()),
                           StandardCharsets.UTF_8);
      }
      catch(IOException e) {
         LOG.debug("Failed to write snapshot owner lease {}", lease, e);
      }
   }

   private record Observed(String counter, long since) {
   }

   static final String LEASE_PREFIX = Tool.PERSISTENT_PREFIX + "snapshot-owner-";
   static final String LEASE_SUFFIX = ".lease";
   static final long REFRESH_INTERVAL = 30000L;
   private static final long MIN_TIMEOUT = 300000L;
   private static final long DEFAULT_FAILURE_DETECTION_TIMEOUT = 10000L;
   private static final long ORPHAN_AGE = 86400000L;
   private static final String REFRESH_THREAD = "Snapshot Owner Lease Refresh";
   // lease path -> number of live tables of this JVM under its node id
   private static final Map<String, Integer> LOCAL_LEASES = new ConcurrentHashMap<>();
   // lease path -> the counter last read and when it was first read on the clock of this JVM
   private static final Map<String, Observed> OBSERVED = new ConcurrentHashMap<>();
   private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());
   private static ScheduledExecutorService refresh;
   // the clock that the counters of the leases of other JVMs are timed on, set by tests
   static LongSupplier clock = System::currentTimeMillis;
   private static final Logger LOG = LoggerFactory.getLogger(SnapshotOwnerLeases.class);
}
