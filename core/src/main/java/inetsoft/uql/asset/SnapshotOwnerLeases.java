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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Bug #78463, the owners of the snapshot copies in {@link SnapshotEmbeddedTableAssembly#FILE_OWNERS_MAP}
 * are Ignite node ids, and an owner that is not in the topology of the local node was taken as
 * gone. Two JVMs that read the same cache directory (the server and the local scheduler, or a
 * cloud-runner client on a mounted cache directory) can both stay alive after a pause longer than
 * the failure detection timeout, as two clusters that never merge. Each one then sees the other
 * as gone and deletes the copies that the other still reads. A copy that only one of them opened
 * after the split is not in the maps of the other at all, so the other deletes it as having no
 * count, both when it closes its own table of the same copy and in its cache sweeps.
 * <p>
 * A JVM that has live snapshot tables under a node id keeps a lease file for that id in the
 * {@link #LEASE_DIR} subdirectory of the directory of the copies. The lease holds a counter and
 * the names of the copies in that directory that the live tables of the JVM read. It is written
 * when a table starts to read copies, before they are copied or read, and every
 * {@link #REFRESH_INTERVAL} with a new counter, which also drops the copies that are not read
 * anymore. A lease is live until its counter has not changed for {@link #getTimeout()} on the
 * clock of the reader, so the clocks of the two JVMs (e.g. on two hosts sharing a network mount)
 * are never compared. A copy listed in a live lease of another JVM is not deleted, and an owner
 * that is not in the topology is gone only if it has no live lease that lists the copy. A JVM of
 * an older version writes no lease, so its owners are judged by the topology alone, as before.
 * With a cache directory per node, the lease of another node is never in the local directory,
 * which is right as that node can't read the local copies.
 * <p>
 * The cache sweeps skip directories, and the name of the lease directory starts with
 * {@link Tool#PERSISTENT_PREFIX}. A lease that is deleted anyway is written again by the next
 * refresh.
 */
final class SnapshotOwnerLeases {
   private SnapshotOwnerLeases() {
   }

   /**
    * Gets the lease file of a node id for the copies in a directory.
    */
   static File getLeaseFile(File dir, String nodeId) {
      return new File(new File(normalize(dir), LEASE_DIR), LEASE_PREFIX + nodeId + LEASE_SUFFIX);
   }

   /**
    * Adds a live table of this JVM under a node id that reads copies in a directory. The lease is
    * written by the {@link #update} that follows, once the table counts its copies.
    */
   static void acquire(File dir, String nodeId) {
      if(dir == null || nodeId == null) {
         return;
      }

      LOCAL_LEASES.merge(getLeaseFile(dir, nodeId).getAbsolutePath(), 1, Integer::sum);
      startRefresh();
   }

   /**
    * Writes the local leases of the copies in a directory, after a table of this JVM started to
    * read copies in it. The copies are listed before this returns.
    */
   static void update(File dir) {
      if(dir == null) {
         return;
      }

      File leaseDir = new File(normalize(dir), LEASE_DIR);

      synchronized(LOCAL_LEASES) {
         for(String path : LOCAL_LEASES.keySet()) {
            File lease = new File(path);

            if(leaseDir.equals(lease.getParentFile())) {
               write(lease);
            }
         }
      }
   }

   /**
    * Removes a live table of this JVM that was added by {@link #acquire}. The lease is deleted
    * when the node id has no live table in the directory anymore. Otherwise the copies that are
    * not read anymore are dropped by the next refresh, keeping them a little longer is safe.
    */
   static void release(File dir, String nodeId) {
      if(dir == null || nodeId == null) {
         return;
      }

      File lease = getLeaseFile(dir, nodeId);
      String path = lease.getAbsolutePath();

      synchronized(LOCAL_LEASES) {
         if(LOCAL_LEASES.computeIfPresent(path, (k, v) -> v <= 1 ? null : v - 1) == null) {
            lease.delete();
         }
      }
   }

   /**
    * Checks if the owner of a copy that is not in the topology of this node is still alive and
    * reads the copy, i.e. if its lease is held by this JVM, or is live and lists the copy.
    *
    * @param file   the absolute path of the copy.
    * @param nodeId the node id of the owner.
    * @param reads  the leases read so far, e.g. in one sweep.
    */
   static boolean isAlive(String file, String nodeId, Reads reads) {
      File copy = normalize(new File(file));
      File dir = copy.getParentFile();

      if(dir == null) {
         return false;
      }

      File lease = getLeaseFile(dir, nodeId);

      if(LOCAL_LEASES.containsKey(lease.getAbsolutePath())) {
         return true;
      }

      Lease read = reads.get(lease);
      return read != null && read.reads(copy.getName());
   }

   /**
    * Gets the copies in a directory that live tables of other JVMs read, from their live leases.
    *
    * @param dir the directory of the copies, relative or absolute.
    *
    * @return a test of the path of a copy.
    */
   static Predicate<String> getFilesInUseElsewhere(File dir) {
      if(dir == null) {
         return path -> false;
      }

      File base = normalize(dir);
      File[] leases = listLeases(base);

      if(leases == null || leases.length == 0) {
         return path -> false;
      }

      Set<String> names = new HashSet<>();
      boolean unknown = false;

      for(File lease : leases) {
         if(LOCAL_LEASES.containsKey(lease.getAbsolutePath())) {
            continue;
         }

         Lease read = read(lease);

         if(read == null) {
            continue;
         }

         if(read.files() == null) {
            unknown = true;
         }
         else {
            names.addAll(read.files());
         }
      }

      boolean anyCopy = unknown;
      return path -> {
         File copy = normalize(new File(path));
         String name = copy.getName();
         return base.equals(copy.getParentFile()) &&
            (names.contains(name) || anyCopy && isCopyName(name));
      };
   }

   /**
    * Deletes the lease of an owner that was found gone, unless it is live, as the owner may
    * still read other copies.
    */
   static void removeDead(String file, String nodeId, Reads reads) {
      File dir = normalize(new File(file)).getParentFile();

      if(dir == null) {
         return;
      }

      File lease = getLeaseFile(dir, nodeId);
      String path = lease.getAbsolutePath();

      if(!LOCAL_LEASES.containsKey(path) && lease.exists() && reads.get(lease) == null) {
         OBSERVED.remove(path);
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
         files = listLeases(normalize(FileSystemService.getInstance().getCacheFolder()));
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

   // the absolute path without "." and "..", the copies and the leases are compared by it
   private static File normalize(File file) {
      return file.toPath().toAbsolutePath().normalize().toFile();
   }

   private static boolean isCopyName(String name) {
      return name.endsWith(COPY_SUFFIX);
   }

   private static File[] listLeases(File dir) {
      return new File(dir, LEASE_DIR).listFiles(
         (d, name) -> name.startsWith(LEASE_PREFIX) && name.endsWith(LEASE_SUFFIX));
   }

   /**
    * Reads the lease of another JVM.
    *
    * @return the lease, with {@code null} files if its content can't be read, or {@code null} if
    *         it is missing or not live.
    */
   private static Lease read(File lease) {
      String path = lease.getAbsolutePath();
      List<String> lines;

      try {
         lines = Files.readAllLines(lease.toPath(), StandardCharsets.UTF_8);
      }
      catch(IOException e) {
         // no lease, the owner is a JVM of an older version or is gone
         if(!lease.exists()) {
            OBSERVED.remove(path);
            return null;
         }

         lines = List.of();
      }

      String counter = lines.isEmpty() ? "" : lines.get(0).trim();
      // e.g. left by a JVM that crashed while it wrote the lease in place, it is live until its
      // time changes no more for the timeout
      boolean unknown = counter.isEmpty();

      if(unknown) {
         counter = "?" + lease.lastModified();
      }

      long now = clock.getAsLong();
      Observed observed = OBSERVED.get(path);

      if(observed == null || !observed.counter().equals(counter)) {
         OBSERVED.put(path, new Observed(counter, now));
      }
      else if(now - observed.since() >= getTimeout()) {
         return null;
      }

      if(unknown) {
         return new Lease(null);
      }

      Set<String> files = new HashSet<>();

      for(int i = 1; i < lines.size(); i++) {
         String name = lines.get(i).trim();

         if(!name.isEmpty()) {
            files.add(name);
         }
      }

      return new Lease(files);
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

   /**
    * Writes a local lease, the caller must hold the lock of {@link #LOCAL_LEASES}. It is written
    * to a temporary file that replaces the lease, so that another JVM never reads a part of it.
    */
   private static void write(File lease) {
      File leaseDir = lease.getParentFile();
      File dir = leaseDir.getParentFile();
      StringBuilder content = new StringBuilder(Long.toString(COUNTER.incrementAndGet()));

      for(String file : SnapshotEmbeddedTableAssembly.getLocalFiles()) {
         File copy = normalize(new File(file));

         if(dir.equals(copy.getParentFile())) {
            content.append('\n').append(copy.getName());
         }
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
      }
      catch(IOException e) {
         LOG.debug("Failed to write snapshot owner lease {}", lease, e);
      }
   }

   /**
    * The leases of other JVMs read once, e.g. for all the owners and copies of one sweep.
    */
   static final class Reads {
      private Lease get(File lease) {
         return leases.computeIfAbsent(lease.getAbsolutePath(), k -> Optional.ofNullable(read(lease)))
            .orElse(null);
      }

      private final Map<String, Optional<Lease>> leases = new HashMap<>();
   }

   // the copies that a live lease lists, null if they are not known
   private record Lease(Set<String> files) {
      boolean reads(String name) {
         return files == null ? isCopyName(name) : files.contains(name);
      }
   }

   private record Observed(String counter, long since) {
   }

   // the subdirectory of the leases in the directory of the copies, the sweeps skip directories
   static final String LEASE_DIR = Tool.PERSISTENT_PREFIX + "snapshot-owners";
   static final String LEASE_PREFIX = "snapshot-owner-";
   static final String LEASE_SUFFIX = ".lease";
   static final long REFRESH_INTERVAL = 30000L;
   private static final String COPY_SUFFIX = "_s.tdat";
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
