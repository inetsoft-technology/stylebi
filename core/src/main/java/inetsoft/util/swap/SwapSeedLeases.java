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
package inetsoft.util.swap;

import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Feature #79134, a registered swap file is protected from the cache sweeps only while the Ignite
 * node id that owns its seed (see {@link XSwapper#SWAP_SEED_MAP}) is in the topology of the
 * sweeping node. Two JVMs that share a cache directory (the server and the local scheduler, or a
 * cloud-runner client on a mounted cache directory) can both stay alive and lose each other from
 * the topology, either in a partition or in a pause longer than the failure detection timeout,
 * without any of them halting. Each one then took the swap files of the other as dead and deleted
 * them once they were past {@link XSwapper#SWAP_FILE_GRACE_PERIOD}.
 * <p>
 * Every {@link XSwapper} keeps a lease file for its seed, whose name is in the files it writes
 * (<tt>s&lt;seed&gt;_...</tt>), in the {@link #LEASE_DIR} subdirectory of the cache directory:
 * <tt>swap-seed-&lt;seed&gt;.lease</tt>. The content is UTF-8, line 1 is a counter, the only
 * thing that a reader interprets, and the optional line 2 is <tt>node=&lt;node id&gt;</tt>, for
 * diagnostics only. A reader ignores any other line, so the format can be extended. The lease is
 * written when the swapper is created, before it can write a swap file, and every
 * {@link #REFRESH_INTERVAL} with a new counter by a dedicated daemon thread, never on the swap
 * path. It is deleted when the swapper stops or the JVM exits normally; a JVM that is halted
 * leaves it. Its leases are removed by the sweeps once they are found stale if the seed is in the
 * seed map of the sweeping node, and any lease that has not been written for a day is removed.
 * <p>
 * A lease is replaced atomically, so normally a reader never sees a part of it. Where the atomic
 * replace fails (e.g. Windows, with the lease open in another process) it is rewritten in place,
 * and a reader that sees a partial or empty lease takes it as live, as its time changes.
 * <p>
 * A lease is live until its counter has not changed for {@link #getTimeout()} on the clock of the
 * reader, so the clocks of two hosts that share a network mount are never compared. The first
 * time that a reader sees a lease it is live. A lease that exists but can't be read is timed on
 * its modification time instead of its counter, and is live until that time stays unchanged for
 * {@link #getTimeout()}. A lease that is missing is not live: the seed is judged by the topology
 * alone, as it is for a JVM of an older version that writes no lease, or with a cache directory
 * per node, where the lease of another node is never in the local directory.
 * <p>
 * The thresholds relate as follows (a unit test pins it):
 * <ul>
 *    <li>{@link #REFRESH_INTERVAL} (30 s) * 2 &lt;= {@link XSwapper#SWAP_FILE_GRACE_PERIOD}
 *    (60 s), two missed writes fit in the window in which a fresh file is exempt anyway.</li>
 *    <li>{@link #getTimeout()} = max(5 min, 3 * <tt>cluster.failureDetectionTimeout</tt>), which
 *    defaults to 10 s, so it is 5 min, the formula of the snapshot owner leases (Bug #78463).
 *    A split happens when a peer is silent for more than the failure detection timeout; the
 *    writer lives in the same JVM, so a pause that causes a split also stops the lease, and the
 *    reader must not take the peer as gone for a pause well past that timeout. The floor lets
 *    the default timeout tolerate a long GC pause. A longer pause than the timeout is taken as
 *    the JVM being gone.</li>
 *    <li>{@link #getTimeout()} is at least ten refresh intervals and more than the grace
 *    period, so ten writes must be missed before a lease is stale.</li>
 * </ul>
 * The lease never shortens or skips {@link XSwapper#SWAP_FILE_GRACE_PERIOD}: the sweeps delete a
 * file only when it is old enough, with the same <tt>&gt;=</tt> as before, and no live lease
 * protects it. The consequence is that the files of a JVM that crashed and left its lease are
 * swept after the timeout counted from the first time that a sweep read the lease, up to the
 * timeout plus the interval of the sweep (about 8 minutes) instead of about 3 minutes. A restarted
 * JVM, or a scheduler that sweeps once when it starts, reads the lease of its crashed predecessor
 * for the first time, so it doesn't reclaim those files; the recurring sweep of the server does,
 * after the timeout. A clean up in the Enterprise Manager right after a crash may need a second
 * run later than the timeout.
 * <p>
 * A lease protects only the files named with its own seed, whether or not that seed or file is in
 * the distributed maps, which a partitioned island may not have. The sweeps skip directories, and
 * the name of the lease directory starts with {@link Tool#PERSISTENT_PREFIX}. The name of a lease
 * has no underscore, so it is never taken for a swap file. A lease that is deleted anyway is
 * written again by the next refresh.
 * <p>
 * A failure to write the lease never reaches the swapper: it is logged, once as a warning, and
 * the seed is judged by the topology alone by the other JVMs, as before. A failure to read a
 * lease in a sweep keeps the file, as a wrong delete costs a swap-in error and a missed one
 * nothing.
 */
final class SwapSeedLeases {
   private SwapSeedLeases() {
   }

   /**
    * Gets the lease file of a seed in a cache directory.
    */
   static File getLeaseFile(File dir, long seed) {
      return new File(new File(normalize(dir), LEASE_DIR), LEASE_PREFIX + seed + LEASE_SUFFIX);
   }

   /**
    * Adds the lease of a seed of this JVM, and writes it before this returns. It never throws.
    *
    * @param dir    the cache directory.
    * @param seed   the seed of the swapper.
    * @param nodeId the local node id, for diagnostics.
    */
   static void acquire(File dir, long seed, String nodeId) {
      try {
         if(dir == null) {
            return;
         }

         File lease = getLeaseFile(dir, seed);

         synchronized(LOCAL) {
            LOCAL.put(lease.getAbsolutePath(), new Local(seed, nodeId));
            write(lease, nodeId);
            startRefresh();
         }
      }
      catch(Throwable e) {
         warn("", "Failed to acquire the swap seed lease", e);
      }
   }

   /**
    * Removes the leases of a seed of this JVM and deletes their files. It never throws.
    */
   static void release(long seed) {
      try {
         synchronized(LOCAL) {
            LOCAL.entrySet().removeIf(entry -> {
               if(entry.getValue().seed() == seed) {
                  deleteQuietly(new File(entry.getKey()));
                  return true;
               }

               return false;
            });
         }
      }
      catch(Throwable e) {
         LOG.debug("Failed to release the swap seed lease", e);
      }
   }

   /**
    * Checks if the JVM of a seed is alive, i.e. if its lease is held by this JVM or is live. A
    * lease that is missing is not live. If the check fails unexpectedly, the seed is taken as
    * alive.
    *
    * @param dir  the cache directory, relative or absolute.
    * @param seed the seed of the swap file.
    */
   static boolean isAlive(File dir, long seed) {
      try {
         if(dir == null) {
            return false;
         }

         File lease = getLeaseFile(dir, seed);

         if(LOCAL.containsKey(lease.getAbsolutePath())) {
            return true;
         }

         return isLive(lease);
      }
      catch(Throwable e) {
         LOG.debug("Unable to check the swap seed lease of {}", seed, e);
         return true;
      }
   }

   /**
    * Deletes the lease of a seed that was found gone, unless it is held by this JVM or is live.
    */
   static void removeDead(File dir, long seed) {
      try {
         if(dir == null) {
            return;
         }

         File lease = getLeaseFile(dir, seed);
         String path = lease.getAbsolutePath();

         if(!LOCAL.containsKey(path) && lease.exists() && !isLive(lease)) {
            OBSERVED.remove(path);
            deleteQuietly(lease);
         }
      }
      catch(Throwable e) {
         LOG.debug("Failed to remove the swap seed lease of {}", seed, e);
      }
   }

   /**
    * Deletes the leases in a directory that have not been written for a day, e.g. of a JVM that
    * was halted and whose seed is not in the seed map anymore. A live lease is written every
    * {@link #REFRESH_INTERVAL}, so the clock of another host can't make it look that old.
    */
   static void removeOrphans(File dir) {
      try {
         if(dir == null) {
            return;
         }

         File[] files = new File(normalize(dir), LEASE_DIR).listFiles(
            (d, name) -> name.startsWith(LEASE_PREFIX) && name.endsWith(LEASE_SUFFIX));

         if(files == null) {
            return;
         }

         long expired = System.currentTimeMillis() - ORPHAN_AGE;

         for(File file : files) {
            String path = file.getAbsolutePath();

            if(!LOCAL.containsKey(path) && file.lastModified() < expired) {
               OBSERVED.remove(path);
               deleteQuietly(file);
            }
         }
      }
      catch(Throwable e) {
         LOG.debug("Failed to remove the orphan swap seed leases", e);
      }
   }

   /**
    * Gets how long the counter of a lease must stay the same before its JVM is gone. It covers a
    * pause of the JVM well past the failure detection timeout of the cluster.
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

   // the absolute path without "." and "..", so the writer and the readers agree on the path
   private static File normalize(File file) {
      return file.toPath().toAbsolutePath().normalize().toFile();
   }

   /**
    * Reads the lease of another JVM.
    *
    * @return false if it is missing or stale: its counter has not changed for the timeout, i.e.
    *         now - firstSeen >= timeout on the clock of the reader.
    */
   private static boolean isLive(File lease) {
      String path = lease.getAbsolutePath();
      String counter;

      try {
         String content = Files.readString(lease.toPath(), StandardCharsets.UTF_8);
         int index = content.indexOf('\n');
         counter = (index < 0 ? content : content.substring(0, index)).trim();
      }
      catch(NoSuchFileException e) {
         OBSERVED.remove(path);
         return false;
      }
      catch(IOException e) {
         if(!lease.exists()) {
            OBSERVED.remove(path);
            return false;
         }

         counter = "";
      }

      if(counter.isEmpty()) {
         // e.g. left by a JVM that crashed while it wrote the lease in place, it is live until
         // its time changes no more for the timeout
         counter = "?" + lease.lastModified();
      }

      long now = clock.getAsLong();
      Observed observed = OBSERVED.get(path);

      if(observed == null || !observed.counter().equals(counter)) {
         OBSERVED.put(path, new Observed(counter, now));
         return true;
      }

      return now - observed.since() < getTimeout();
   }

   private static void startRefresh() {
      if(refresh != null) {
         return;
      }

      ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread thread = new Thread(r, REFRESH_THREAD);
         thread.setDaemon(true);
         return thread;
      });
      executor.scheduleWithFixedDelay(SwapSeedLeases::refresh, REFRESH_INTERVAL,
                                      REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
      refresh = executor;
      Runtime.getRuntime().addShutdownHook(
         new Thread(SwapSeedLeases::deleteLocal, REFRESH_THREAD + " Shutdown"));
   }

   /**
    * Writes the local leases again. It runs on the refresh thread, and never throws, as that
    * would cancel the schedule.
    */
   static void refresh() {
      try {
         synchronized(LOCAL) {
            for(Map.Entry<String, Local> entry : LOCAL.entrySet()) {
               write(new File(entry.getKey()), entry.getValue().nodeId());
            }
         }
      }
      catch(Throwable e) {
         warn("", "Failed to refresh the swap seed leases", e);
      }
   }

   // the leases of a halted or killed JVM are left, and are removed when found gone
   static void deleteLocal() {
      try {
         synchronized(LOCAL) {
            for(String path : LOCAL.keySet()) {
               deleteQuietly(new File(path));
            }
         }
      }
      catch(Throwable e) {
         LOG.debug("Failed to delete the swap seed leases", e);
      }
   }

   private static void deleteQuietly(File file) {
      try {
         Files.deleteIfExists(file.toPath());
      }
      catch(Exception e) {
         LOG.debug("Failed to delete {}", file, e);
      }
   }

   /**
    * Writes a local lease. It is written to a temporary file that replaces the lease, so that
    * normally another JVM never reads a part of it. Where the replace fails (e.g. Windows, with
    * the lease open elsewhere) the lease is rewritten in place, and a reader that sees a partial or
    * empty lease takes it as live. A failure is logged and does not throw.
    */
   private static void write(File lease, String nodeId) {
      File leaseDir = lease.getParentFile();
      StringBuilder content = new StringBuilder(Long.toString(COUNTER.incrementAndGet()));

      if(nodeId != null) {
         content.append("\nnode=").append(nodeId);
      }

      Path target = lease.toPath();
      Path temp = new File(leaseDir, lease.getName() + ".tmp").toPath();

      try {
         Files.createDirectories(leaseDir.toPath());
         Files.writeString(temp, content, StandardCharsets.UTF_8);

         try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                       StandardCopyOption.ATOMIC_MOVE);
         }
         catch(IOException e) {
            // e.g. the lease is open in another process on Windows
            Files.deleteIfExists(temp);
            Files.writeString(target, content, StandardCharsets.UTF_8);
         }

         if(failing.remove(lease.getAbsolutePath())) {
            LOG.info("The swap seed lease {} is written again", lease);
         }
      }
      catch(Throwable e) {
         try {
            Files.deleteIfExists(temp);
         }
         catch(Exception ignore) {
         }

         warn(lease.getAbsolutePath(), "Failed to write the swap seed lease " + lease, e);
      }
   }

   // a warning for the first failure after a success, then debug
   private static void warn(String key, String message, Throwable e) {
      if(failing.add(key)) {
         LOG.warn("{}, other JVMs judge the swap files of this JVM by the cluster topology alone: {}",
                  message, e.toString());
      }

      LOG.debug(message, e);
   }

   private record Local(long seed, String nodeId) {
   }

   private record Observed(String counter, long since) {
   }

   // the subdirectory of the leases in the cache directory, the sweeps skip directories
   static final String LEASE_DIR = Tool.PERSISTENT_PREFIX + "swap-seeds";
   static final String LEASE_PREFIX = "swap-seed-";
   static final String LEASE_SUFFIX = ".lease";
   static final long REFRESH_INTERVAL = 30000L;
   static final String REFRESH_THREAD = "Swap Seed Lease Refresh";
   private static final long MIN_TIMEOUT = 300000L;
   private static final long DEFAULT_FAILURE_DETECTION_TIMEOUT = 10000L;
   private static final long ORPHAN_AGE = 86400000L;
   // lease path -> the seed and node id of this JVM
   private static final Map<String, Local> LOCAL = new ConcurrentHashMap<>();
   // lease path -> the counter last read and when it was first read on the clock of this JVM
   private static final Map<String, Observed> OBSERVED = new ConcurrentHashMap<>();
   private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());
   // the leases (by path) whose last write failed, a failure is a warning only the first time
   private static final Set<String> failing = ConcurrentHashMap.newKeySet();
   private static ScheduledExecutorService refresh;
   // the clock that the counters of the leases of other JVMs are timed on, set by tests
   static LongSupplier clock = System::currentTimeMillis;
   private static final Logger LOG = LoggerFactory.getLogger(SwapSeedLeases.class);
}
