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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.test.*;
import inetsoft.uql.asset.SnapshotEmbeddedTableAssembly;
import inetsoft.util.Tool;
import inetsoft.util.config.InetsoftConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #79134, the lease that a swapper keeps for its seed in the cache directory, which the
 * cache sweeps of the other JVMs read when the topology says that the owner of a swap file is
 * gone. The on-disk names are written as literals here, as they are an external contract that
 * older and newer versions read.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class SwapSeedLeasesTest {
   @BeforeEach
   void setUp() throws Exception {
      originalTimeout = InetsoftConfig.getInstance().getCluster().getFailureDetectionTimeout();
      now.set(1_000_000L);
      SwapSeedLeases.clock = now::get;
      logger = (Logger) LoggerFactory.getLogger(SwapSeedLeases.class);
      oldLevel = logger.getLevel();
      logger.setLevel(Level.DEBUG);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(oldLevel);
      SwapSeedLeases.clock = System::currentTimeMillis;
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(originalTimeout);

      for(long seed : acquired) {
         SwapSeedLeases.release(seed);
      }

      acquired.clear();
   }

   // A1
   @Test
   void acquireWritesLeaseNamingSeedAndRefreshAdvancesCounter() throws Exception {
      File dir = newDir();
      acquire(dir, 5001L, "node-a");

      File lease = new File(new File(dir, "__persistent__swap-seeds"), "swap-seed-5001.lease");
      assertTrue(lease.isFile(), "no lease written by acquire");
      assertEquals(lease.getAbsolutePath(), SwapSeedLeases.getLeaseFile(dir, 5001L).getAbsolutePath());

      List<String> lines = Files.readAllLines(lease.toPath(), StandardCharsets.UTF_8);
      long first = Long.parseLong(lines.get(0));
      assertEquals("node=node-a", lines.get(1));

      SwapSeedLeases.refresh();
      long second = Long.parseLong(Files.readAllLines(lease.toPath()).get(0));
      assertTrue(second > first, "counter did not advance: " + first + " -> " + second);

      SwapSeedLeases.refresh();
      assertTrue(Long.parseLong(Files.readAllLines(lease.toPath()).get(0)) > second);
   }

   // A1, a relative or unnormalised directory spelling gives the same lease
   @Test
   void leasePathIsTheSameForAnyspellingOfTheDirectory() throws Exception {
      File dir = newDir();
      File spelled = new File(new File(dir, "x"), "..");
      assertEquals(SwapSeedLeases.getLeaseFile(dir, 5002L), SwapSeedLeases.getLeaseFile(spelled, 5002L));
   }

   // A6
   @Test
   void releaseDeletesOwnLease() {
      File dir = newDir();
      acquire(dir, 5003L, "n");
      File lease = SwapSeedLeases.getLeaseFile(dir, 5003L);
      assertTrue(lease.exists());

      SwapSeedLeases.release(5003L);
      assertFalse(lease.exists(), "lease left after release");
      assertFalse(SwapSeedLeases.isAlive(dir, 5003L), "a released lease is still alive");

      // refresh does not bring a released lease back
      SwapSeedLeases.refresh();
      assertFalse(lease.exists());
   }

   // A6
   @Test
   void shutdownHookActionDeletesAllLocalLeases() {
      File dir = newDir();
      acquire(dir, 5004L, "n");
      acquire(dir, 5005L, "n");

      SwapSeedLeases.deleteLocal();

      assertFalse(SwapSeedLeases.getLeaseFile(dir, 5004L).exists());
      assertFalse(SwapSeedLeases.getLeaseFile(dir, 5005L).exists());
   }

   // A6
   @Test
   void deletedLeaseIsRewrittenByNextRefresh() {
      File dir = newDir();
      acquire(dir, 5006L, "n");
      File lease = SwapSeedLeases.getLeaseFile(dir, 5006L);
      assertTrue(lease.delete());
      // e.g. a clean up of the whole cache directory
      assertTrue(lease.getParentFile().delete());

      SwapSeedLeases.refresh();
      assertTrue(lease.isFile(), "a deleted lease was not written again");
   }

   // A6
   @Test
   void leaseNameNeverMatchesSwapFilePatterns() {
      File dir = newDir();
      File lease = SwapSeedLeases.getLeaseFile(dir, 1795034400123L);
      String name = lease.getName();

      assertEquals("swap-seed-1795034400123.lease", name);
      assertNull(XSwapper.getSwapFileSeed(name));
      assertFalse(name.endsWith(".tdat"));
      assertFalse(name.matches(".*\\.\\d+"));
      assertTrue(lease.getParentFile().getName().startsWith(Tool.PERSISTENT_PREFIX));
      assertEquals("__persistent__swap-seeds", lease.getParentFile().getName());
      // not the directory of the snapshot owner leases of Bug #78463
      assertNotEquals("__persistent__snapshot-owners", lease.getParentFile().getName());
   }

   // A3, a lease that was seen once and not renewed
   @Test
   void firstObservationOfALeaseIsLive() throws Exception {
      File dir = newDir();
      writeForeign(dir, 6001L, "100");

      // however old the file is, the reader's own clock decides
      assertTrue(new File(SwapSeedLeases.getLeaseFile(dir, 6001L).getPath())
                    .setLastModified(1000L));
      assertTrue(SwapSeedLeases.isAlive(dir, 6001L));
   }

   // A3 and A5, the boundary is the timeout, as the existing age gate of the sweeps is >=
   @Test
   void leaseIsStaleOnceItsCounterHasNotChangedForTheTimeout() throws Exception {
      File dir = newDir();
      writeForeign(dir, 6002L, "100");
      long timeout = SwapSeedLeases.getTimeout();

      assertTrue(SwapSeedLeases.isAlive(dir, 6002L), "first look");
      now.addAndGet(timeout - 1);
      assertTrue(SwapSeedLeases.isAlive(dir, 6002L), "one ms before the timeout");
      now.addAndGet(1);
      assertFalse(SwapSeedLeases.isAlive(dir, 6002L), "at the timeout");
      now.addAndGet(timeout);
      assertFalse(SwapSeedLeases.isAlive(dir, 6002L), "long after the timeout");
   }

   // A2, the writer is alive
   @Test
   void leaseWhoseCounterAdvancesStaysLiveForever() throws Exception {
      File dir = newDir();
      long timeout = SwapSeedLeases.getTimeout();

      for(int i = 0; i < 10; i++) {
         writeForeign(dir, 6003L, Integer.toString(100 + i));
         assertTrue(SwapSeedLeases.isAlive(dir, 6003L), "round " + i);
         now.addAndGet(timeout - 1);
      }
   }

   // A3, a counter that is reset to an older value is a change as well
   @Test
   void anyChangeOfTheCounterRestartsTheClock() throws Exception {
      File dir = newDir();
      long timeout = SwapSeedLeases.getTimeout();
      writeForeign(dir, 6004L, "200");
      assertTrue(SwapSeedLeases.isAlive(dir, 6004L));
      now.addAndGet(timeout - 1);
      writeForeign(dir, 6004L, "100");
      assertTrue(SwapSeedLeases.isAlive(dir, 6004L));
      now.addAndGet(timeout - 1);
      assertTrue(SwapSeedLeases.isAlive(dir, 6004L));
      now.addAndGet(1);
      assertFalse(SwapSeedLeases.isAlive(dir, 6004L));
   }

   // A3, unknown lines after the counter are ignored (forward compatibility)
   @Test
   void onlyTheFirstLineIsTheCounter() throws Exception {
      File dir = newDir();
      long timeout = SwapSeedLeases.getTimeout();
      write(dir, 6005L, "7\nnode=a\nfuture=1\n");
      assertTrue(SwapSeedLeases.isAlive(dir, 6005L));
      now.addAndGet(timeout - 1);
      // the other lines change, the counter does not
      write(dir, 6005L, "7\nnode=b\nfuture=2\n");
      now.addAndGet(1);
      assertFalse(SwapSeedLeases.isAlive(dir, 6005L), "a diagnostic line renewed the lease");
   }

   // A3, G2: unreadable content is timed on the modification time
   @Test
   void leaseWithUnreadableContentIsLiveUntilItsTimeStopsChanging() throws Exception {
      File dir = newDir();
      File lease = SwapSeedLeases.getLeaseFile(dir, 6006L);
      assertTrue(lease.getParentFile().mkdirs());
      assertTrue(lease.createNewFile());
      assertTrue(lease.setLastModified(5_000_000L));
      long timeout = SwapSeedLeases.getTimeout();

      assertTrue(SwapSeedLeases.isAlive(dir, 6006L), "first look at an empty lease");
      now.addAndGet(timeout - 1);
      assertTrue(SwapSeedLeases.isAlive(dir, 6006L));
      // its time changed, as a writer that is still alive would change it
      assertTrue(lease.setLastModified(6_000_000L));
      assertTrue(SwapSeedLeases.isAlive(dir, 6006L), "a changed time is a new first look");
      now.addAndGet(timeout - 1);
      assertTrue(SwapSeedLeases.isAlive(dir, 6006L), "a changed time must restart the clock");
      now.addAndGet(1);
      assertFalse(SwapSeedLeases.isAlive(dir, 6006L), "unchanged for the timeout");
   }

   // A3 and A7, a lease that is a directory can't be read at all
   @Test
   void leaseThatCanNotBeReadAsAFileIsTimedOnItsModificationTime() {
      File dir = newDir();
      File lease = SwapSeedLeases.getLeaseFile(dir, 6007L);
      assertTrue(lease.mkdirs());

      assertTrue(SwapSeedLeases.isAlive(dir, 6007L));
      now.addAndGet(SwapSeedLeases.getTimeout());
      assertFalse(SwapSeedLeases.isAlive(dir, 6007L));
   }

   // A7
   @Test
   void absentLeaseDirIsNotAlive() {
      File dir = newDir();
      assertFalse(SwapSeedLeases.isAlive(dir, 6008L));
      assertFalse(SwapSeedLeases.isAlive(new File(dir, "does-not-exist"), 6008L));
      assertFalse(SwapSeedLeases.isAlive(null, 6008L));
   }

   // A7, a lease that vanishes after it was seen is not alive and starts over when it comes back
   @Test
   void missingLeaseIsNotAliveAndForgotten() throws Exception {
      File dir = newDir();
      long timeout = SwapSeedLeases.getTimeout();
      writeForeign(dir, 6009L, "1");
      assertTrue(SwapSeedLeases.isAlive(dir, 6009L));
      now.addAndGet(timeout - 1);
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6009L).delete());
      assertFalse(SwapSeedLeases.isAlive(dir, 6009L));

      // the same counter again, a JVM that came back: a new first look
      now.addAndGet(timeout);
      writeForeign(dir, 6009L, "1");
      assertTrue(SwapSeedLeases.isAlive(dir, 6009L));
   }

   // A3, G3
   @Test
   void removeDeadDeletesAStaleLeaseButNotALiveOrLocalOne() throws Exception {
      File dir = newDir();
      long timeout = SwapSeedLeases.getTimeout();
      writeForeign(dir, 6010L, "1");
      writeForeign(dir, 6011L, "1");
      acquire(dir, 6012L, "me");
      assertTrue(SwapSeedLeases.isAlive(dir, 6010L));
      assertTrue(SwapSeedLeases.isAlive(dir, 6011L));

      now.addAndGet(timeout);
      // 6011 was renewed
      writeForeign(dir, 6011L, "2");

      SwapSeedLeases.removeDead(dir, 6010L);
      SwapSeedLeases.removeDead(dir, 6011L);
      SwapSeedLeases.removeDead(dir, 6012L);

      assertFalse(SwapSeedLeases.getLeaseFile(dir, 6010L).exists(), "stale lease kept");
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6011L).exists(), "live lease deleted");
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6012L).exists(), "own lease deleted");
   }

   @Test
   void removeOrphansDeletesLeasesNotWrittenForADayOnly() throws Exception {
      File dir = newDir();
      writeForeign(dir, 6013L, "1");
      writeForeign(dir, 6014L, "1");
      acquire(dir, 6015L, "me");
      long day = 86400000L;
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6013L)
                    .setLastModified(System.currentTimeMillis() - day - 60000L));
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6015L)
                    .setLastModified(System.currentTimeMillis() - 5 * day));

      SwapSeedLeases.removeOrphans(dir);
      // and nothing to do for a directory without leases
      SwapSeedLeases.removeOrphans(new File(dir, "none"));
      SwapSeedLeases.removeOrphans(null);

      assertFalse(SwapSeedLeases.getLeaseFile(dir, 6013L).exists());
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6014L).exists());
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 6015L).exists(), "own lease of this JVM");
   }

   // A5
   @ParameterizedTest
   @ValueSource(longs = { -1L, 0L, 10_000L, 60_000L, 180_000L, 600_000L })
   void thresholdsRelateToGracePeriodAndFailureDetectionTimeout(long fdt) {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(fdt);
      assertThresholds(fdt > 0 ? fdt : 10_000L);
   }

   // A5
   @Test
   void thresholdsWithAnUnsetFailureDetectionTimeout() {
      InetsoftConfig.getInstance().getCluster().setFailureDetectionTimeout(null);
      assertThresholds(10_000L);
   }

   private void assertThresholds(long fdt) {
      long timeout = SwapSeedLeases.getTimeout();
      // the formula of the snapshot owner leases (Bug #78463), the two are in different packages
      // and private, so a change to one of them shows against this literal
      assertEquals(Math.max(300_000L, 3 * fdt), timeout);
      assertEquals(30_000L, SwapSeedLeases.REFRESH_INTERVAL);
      assertTrue(SwapSeedLeases.REFRESH_INTERVAL * 2 <= XSwapper.SWAP_FILE_GRACE_PERIOD);
      assertTrue(SwapSeedLeases.REFRESH_INTERVAL * 10 <= timeout);
      assertTrue(timeout > XSwapper.SWAP_FILE_GRACE_PERIOD);
      assertTrue(timeout >= 3 * fdt);
      assertTrue(timeout >= 300_000L);
      // the grace period is not changed by the lease
      assertEquals(60_000L, XSwapper.SWAP_FILE_GRACE_PERIOD);
   }

   // C3
   @Test
   void writeFailureDoesNotThrowAndRefreshKeepsRunning() throws Exception {
      File dir = newDir();
      // a regular file where the directory of the leases must be
      File blocker = new File(dir, "__persistent__swap-seeds");
      assertTrue(blocker.createNewFile());

      assertDoesNotThrow(() -> SwapSeedLeases.acquire(dir, 7001L, "n"));
      acquired.add(7001L);
      assertDoesNotThrow(SwapSeedLeases::refresh);
      assertDoesNotThrow(SwapSeedLeases::refresh);
      assertFalse(SwapSeedLeases.getLeaseFile(dir, 7001L).exists());
      // the JVM still counts as its own owner of the lease
      assertTrue(SwapSeedLeases.isAlive(dir, 7001L));

      // the disk recovers: the next tick writes the lease
      assertTrue(blocker.delete());
      SwapSeedLeases.refresh();
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 7001L).isFile(), "refresh did not recover");

      assertDoesNotThrow(() -> SwapSeedLeases.release(7001L));
   }

   // C3
   @Test
   void acquireWithoutADirectoryDoesNotThrow() {
      assertDoesNotThrow(() -> SwapSeedLeases.acquire(null, 7002L, "n"));
      assertDoesNotThrow(() -> SwapSeedLeases.release(7002L));
      assertDoesNotThrow(() -> SwapSeedLeases.release(7003L));
   }

   // C3
   @Test
   void warnsOnceThenDebugAndLogsTheRecovery() throws Exception {
      logger.setLevel(Level.INFO);
      File dir = newDir();
      File blocker = new File(dir, "__persistent__swap-seeds");
      assertTrue(blocker.createNewFile());

      SwapSeedLeases.acquire(dir, 7004L, "n");
      acquired.add(7004L);
      SwapSeedLeases.refresh();
      SwapSeedLeases.refresh();
      SwapSeedLeases.refresh();

      assertEquals(1, count(Level.WARN), "one warning for the first failure only");

      assertTrue(blocker.delete());
      SwapSeedLeases.refresh();
      SwapSeedLeases.refresh();
      assertEquals(1, count(Level.INFO), "one message for the recovery");
      assertEquals(1, count(Level.WARN));

      // a new failure after a success is a warning again
      Files.deleteIfExists(SwapSeedLeases.getLeaseFile(dir, 7004L).toPath());
      Files.deleteIfExists(SwapSeedLeases.getLeaseFile(dir, 7004L).getParentFile().toPath());
      assertTrue(blocker.createNewFile());
      SwapSeedLeases.refresh();
      assertEquals(2, count(Level.WARN));
      assertTrue(blocker.delete());
      SwapSeedLeases.refresh();
   }

   // C3, D8: an unexpected failure while checking a lease keeps the file
   @Test
   void readFailureTreatsSeedAsNotDead() throws Exception {
      File dir = newDir();
      writeForeign(dir, 7005L, "1");
      SwapSeedLeases.clock = () -> {
         throw new IllegalStateException("clock failure");
      };

      assertTrue(SwapSeedLeases.isAlive(dir, 7005L));
      assertDoesNotThrow(() -> SwapSeedLeases.removeDead(dir, 7005L));
      assertTrue(SwapSeedLeases.getLeaseFile(dir, 7005L).exists(),
                 "removeDead deleted a lease that could not be checked");
   }

   // C4
   @Test
   void refreshRunsOnDedicatedDaemonThread() throws Exception {
      acquire(newDir(), 7006L, "n");

      Thread found = null;

      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         if("Swap Seed Lease Refresh".equals(thread.getName())) {
            found = thread;
         }
      }

      assertNotNull(found, "no refresh thread");
      assertTrue(found.isDaemon());
      assertEquals("Swap Seed Lease Refresh", SwapSeedLeases.REFRESH_THREAD);
      assertFalse(found.getName().startsWith("XSwapper"));
   }

   // C4
   @Test
   void concurrentAcquireReleaseRefreshNeverThrows() throws Exception {
      File dir = newDir();
      ExecutorService pool = Executors.newFixedThreadPool(6);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicInteger count = new AtomicInteger();
      List<Future<?>> futures = new java.util.ArrayList<>();

      for(int t = 0; t < 6; t++) {
         final int id = t;
         futures.add(pool.submit(() -> {
            try {
               for(int i = 0; i < 100; i++) {
                  long seed = 8000L + id;

                  switch(id % 3) {
                  case 0 -> SwapSeedLeases.acquire(dir, seed, "n");
                  case 1 -> SwapSeedLeases.release(seed - 1);
                  default -> SwapSeedLeases.refresh();
                  }

                  SwapSeedLeases.isAlive(dir, seed);
                  count.incrementAndGet();
               }
            }
            catch(Throwable e) {
               failure.compareAndSet(null, e);
            }
         }));
      }

      for(Future<?> future : futures) {
         future.get(60, TimeUnit.SECONDS);
      }

      pool.shutdownNow();
      assertNull(failure.get(), () -> String.valueOf(failure.get()));
      assertEquals(600, count.get());

      for(int t = 0; t < 6; t++) {
         acquired.add(8000L + t);
      }
   }

   // C1
   @Test
   void swapLeaseDirectoryDoesNotAffectSnapshotLeaseListing() throws Exception {
      File dir = newDir();
      File copy = new File(dir, "t1_s.tdat");
      Files.write(copy.toPath(), new byte[] { 1 });
      File snapshotDir = new File(dir, "__persistent__snapshot-owners");
      assertTrue(snapshotDir.mkdirs());
      Files.writeString(new File(snapshotDir, "snapshot-owner-other.lease").toPath(),
                        "5\nt1_s.tdat\n");

      boolean before = SnapshotEmbeddedTableAssembly.getFilesInUseElsewhere(dir)
         .test(copy.getAbsolutePath());
      boolean otherBefore = SnapshotEmbeddedTableAssembly.getFilesInUseElsewhere(dir)
         .test(new File(dir, "t2_s.tdat").getAbsolutePath());
      assertTrue(before);
      assertFalse(otherBefore);

      acquire(dir, 9001L, "n");
      writeForeign(dir, 9002L, "1");

      assertTrue(SnapshotEmbeddedTableAssembly.getFilesInUseElsewhere(dir)
                    .test(copy.getAbsolutePath()));
      assertFalse(SnapshotEmbeddedTableAssembly.getFilesInUseElsewhere(dir)
                     .test(new File(dir, "t2_s.tdat").getAbsolutePath()));
      assertFalse(SnapshotEmbeddedTableAssembly.getFilesInUseElsewhere(dir)
                     .test(SwapSeedLeases.getLeaseFile(dir, 9002L).getAbsolutePath()));
   }

   private long count(Level level) {
      return appender.list.stream().filter(e -> e.getLevel() == level).count();
   }

   private void acquire(File dir, long seed, String nodeId) {
      SwapSeedLeases.acquire(dir, seed, nodeId);
      acquired.add(seed);
   }

   private File newDir() {
      try {
         return Files.createTempDirectory(root, "d").toFile();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   // a lease of another JVM, in the documented format
   private void writeForeign(File dir, long seed, String counter) throws Exception {
      write(dir, seed, counter + "\nnode=foreign\n");
   }

   private void write(File dir, long seed, String content) throws Exception {
      File leaseDir = new File(dir, "__persistent__swap-seeds");
      Files.createDirectories(leaseDir.toPath());
      Files.writeString(new File(leaseDir, "swap-seed-" + seed + ".lease").toPath(), content,
                        StandardCharsets.UTF_8);
   }

   @TempDir
   Path root;
   private final AtomicLong now = new AtomicLong();
   private final java.util.List<Long> acquired = new java.util.ArrayList<>();
   private Long originalTimeout;
   private Logger logger;
   private Level oldLevel;
   private ListAppender<ILoggingEvent> appender;
}
