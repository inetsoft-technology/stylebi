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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.test.*;
import inetsoft.util.ClearOldCacheFilesRunnable;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature #79134, a registered swap file of a JVM that is not in the topology of the sweeping node
 * is kept while the lease of its seed in the cache directory is live, by each of the four places
 * that sweep the cache directory, and still deleted when the lease is stale or missing. The
 * topology of the cluster here has a dead node (not in the topology) and another live node.
 * The leases of the other JVMs are written in the documented format, with literal names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   XSwapperSwapLeaseSweepTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperSwapLeaseSweepTest {
   @BeforeEach
   void setUp() throws Exception {
      now.set(2_000_000L);
      SwapSeedLeases.clock = now::get;
      cacheDir = FileSystemService.getInstance().getFile(
         FileSystemService.getInstance().getCacheDirectory());
   }

   @AfterEach
   void cleanUp() throws Exception {
      SwapSeedLeases.clock = System::currentTimeMillis;
      Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      for(long seed : usedSeeds) {
         seeds.remove(seed);
         Files.deleteIfExists(leaseFile(seed).toPath());
      }

      File blocker = leaseDir();

      if(blocker.isFile()) {
         Files.delete(blocker.toPath());
      }

      created.clear();
      usedSeeds.clear();
   }

   // A1
   @Test
   void xswapperConstructionWritesLeaseForItsSeed() throws Exception {
      // another seed than the bean's, which is the start time in milliseconds
      Thread.sleep(5L);
      XSwapper swapper = new XSwapper();

      try {
         long seed = XSwapper.getSwapFileSeed(swapper.getPrefix() + "_s.tdat");
         usedSeeds.add(seed);
         File lease = leaseFile(seed);
         assertTrue(lease.isFile(), "no lease for the seed of the new swapper");

         List<String> lines = Files.readAllLines(lease.toPath(), StandardCharsets.UTF_8);
         long first = Long.parseLong(lines.get(0));
         assertEquals("node=" + Cluster.getInstance().getLocalNodeId(), lines.get(1));

         SwapSeedLeases.refresh();
         long second = Long.parseLong(Files.readAllLines(lease.toPath()).get(0));
         assertTrue(second > first, "counter did not advance");
         assertTrue(SwapSeedLeases.isAlive(cacheDir, seed));

         // stop and start, e.g. of a context that is refreshed
         swapper.stop();
         assertFalse(lease.exists(), "stop() left the lease");
         swapper.start();
         assertTrue(lease.isFile(), "start() did not write the lease again");
      }
      finally {
         swapper.stop();
      }

      waitForSweepThreads();
   }

   // the bean of the context writes its lease too, it is what the sweeps of the JVM protect
   @Test
   void swapperBeanKeepsALeaseForItsSeed() {
      long seed = XSwapper.getSwapFileSeed(XSwapper.getSwapper().getPrefix() + "_s.tdat");
      assertTrue(leaseFile(seed).isFile());
   }

   // A2, with the owner of the seed missing from the topology
   @ParameterizedTest
   @EnumSource(Entry.class)
   void liveLeaseKeepsRegisteredFileOfOwnerMissingFromTopology(Entry entry) throws Exception {
      Fixture fixture = new Fixture();
      writeLease(fixture.liveSeed, "10");

      entry.ageForDataCache(fixture);
      run(entry);

      assertFalse(fixture.dead.exists(), "the file of a dead JVM without a lease survived");
      assertTrue(fixture.live.exists(), entry + " deleted the file of a JVM with a live lease");
      fixture.assertEntriesKept();
   }

   // A2 and A3, the same sweeps without a lease, or with a lease that is stale
   @ParameterizedTest
   @EnumSource(Entry.class)
   void fileOfOwnerWithoutLeaseIsSweptAsBefore(Entry entry) throws Exception {
      Fixture fixture = new Fixture();

      entry.ageForDataCache(fixture);
      run(entry);

      assertFalse(fixture.live.exists(), entry + " kept the file of a JVM without a lease");

      // clearDataCache deletes one file, and doesn't remove the entries
      if(entry != Entry.CLEAR_DATA_CACHE) {
         assertFalse(fixture.dead.exists());
         assertFalse(Cluster.getInstance().<String, Integer>getMap(XSwapper.SWAP_FILE_MAP)
                        .containsKey(fixture.live.getAbsolutePath()), "stale entry kept");
      }
   }

   // A2, D2: the seed and the file are not in the maps of this node, e.g. of a partitioned island
   @Test
   void liveLeaseKeepsFileWhoseSeedIsNotInSeedMap() throws Exception {
      long live = nextSeed();
      long other = nextSeed();
      File kept = createUnregistered("s" + live + "_1.tdat");
      File swept = createUnregistered("s" + other + "_1.tdat");
      writeLease(live, "1");

      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertTrue(kept.exists(), "the file of a seed with a live lease was deleted");
      assertFalse(swept.exists(), "the file of a seed without a lease survived");
      assertTrue(leaseFile(live).exists());

      // and in the clean up
      File kept2 = createUnregistered("s" + live + "_2_slist.swap");
      File swept2 = createUnregistered("s" + other + "_2_slist.swap");
      FileSystemService.getInstance().clearCacheFiles(null);
      waitUntilDeleted(swept2);
      assertTrue(kept2.exists(), "clearCacheFiles deleted a file of a live lease");
      assertTrue(kept.exists());
   }

   // A2
   @Test
   void liveLeaseKeepsSeedAndMapEntriesFromRemoveStaleEntries() throws Exception {
      Fixture fixture = new Fixture();
      writeLease(fixture.liveSeed, "10");
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
      Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);

      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertFalse(seeds.containsKey(fixture.deadSeed), "dead seed kept");
      assertFalse(map.containsKey(fixture.dead.getAbsolutePath()), "dead entry kept");
      assertTrue(seeds.containsKey(fixture.liveSeed), "seed with a live lease removed");
      assertEquals(DEAD_NODE, seeds.get(fixture.liveSeed));
      assertTrue(map.containsKey(fixture.live.getAbsolutePath()), "entry with a live lease removed");
      assertTrue(leaseFile(fixture.liveSeed).exists(), "live lease deleted");
      assertFalse(leaseFile(fixture.deadSeed).exists());
   }

   // A2, past the grace period and past the timeout in total, while the counter advances
   @Test
   void fileSurvivesRepeatedSweepsPastGraceWhileCounterAdvances() throws Exception {
      Fixture fixture = new Fixture();
      long timeout = SwapSeedLeases.getTimeout();

      for(int i = 0; i < 6; i++) {
         writeLease(fixture.liveSeed, Integer.toString(10 + i));
         XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
         assertTrue(fixture.live.exists(), "deleted in round " + i);
         now.addAndGet(timeout / 2);
      }

      assertTrue(now.get() - 2_000_000L > timeout, "the test must run longer than the timeout");

      // the owner is gone for good: the counter stays, and the lease is stale a timeout after
      // the sweep that saw the last counter, which was half a timeout ago
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      assertTrue(fixture.live.exists());
      now.addAndGet(timeout - timeout / 2 - 1);
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      assertTrue(fixture.live.exists(), "deleted one ms before the timeout");
      now.addAndGet(1);
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      assertFalse(fixture.live.exists(), "a stale lease still protects the file");
   }

   // A3, the sweeps are two instances: the clock of the reader runs across them
   @Test
   void fileOfOwnerWithStaleLeaseIsSweptAfterGraceAndItsLeaseIsDeleted() throws Exception {
      Fixture fixture = new Fixture();
      writeLease(fixture.liveSeed, "10");
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);

      // the first look is live, however old the file is
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      assertTrue(fixture.live.exists());
      assertTrue(leaseFile(fixture.liveSeed).exists());

      now.addAndGet(SwapSeedLeases.getTimeout() + 1);
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertFalse(fixture.live.exists(), "the file of a JVM with a stale lease survived");
      assertFalse(seeds.containsKey(fixture.liveSeed), "the seed of a stale lease was kept");
      assertFalse(leaseFile(fixture.liveSeed).exists(), "the stale lease was kept");
   }

   // C2, the lease never skips the grace period, with a stale lease or none
   @Test
   void fileWithinGraceIsKeptEvenWhenLeaseIsStaleAndOwnerGone() throws Exception {
      Fixture fixture = new Fixture();
      writeLease(fixture.liveSeed, "10");
      assertTrue(fixture.live.setLastModified(System.currentTimeMillis()));
      assertTrue(fixture.dead.setLastModified(System.currentTimeMillis()));
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      now.addAndGet(SwapSeedLeases.getTimeout() * 2);
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertTrue(fixture.live.exists(), "a file within the grace period was deleted");
      assertTrue(fixture.dead.exists(), "a file within the grace period was deleted");
      assertEquals(60_000L, XSwapper.SWAP_FILE_GRACE_PERIOD);
   }

   // A7, a node with its own cache directory never has the lease of the other node in it
   @Test
   void perNodeCacheDirectoryLeaseOfOtherNodeIsNotConsulted(@TempDir Path otherNodeDir)
      throws Exception
   {
      Fixture fixture = new Fixture();
      File leases = new File(otherNodeDir.toFile(), "__persistent__swap-seeds");
      assertTrue(leases.mkdirs());
      Files.writeString(new File(leases, "swap-seed-" + fixture.liveSeed + ".lease").toPath(),
                        "10\nnode=other\n");
      assertTrue(SwapSeedLeases.isAlive(otherNodeDir.toFile(), fixture.liveSeed));

      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertFalse(fixture.live.exists(), "the lease of another directory protected a file");
   }

   // C5
   @Test
   void liveLeaseOfOneSeedDoesNotProtectOtherSeedsOrNonSwapFiles() throws Exception {
      long live = nextSeed();
      long expired = nextSeed();
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(live, DEAD_NODE);
      seeds.put(expired, DEAD_NODE);
      File mine = createRegistered("s" + live + "_1_s.tdat");
      File other = createRegistered("s" + expired + "_1_s.tdat");
      File noSeed = createUnregistered("t99_s.tdat");
      File plain = createUnregistered("foo.tdat");
      // a seed that is a prefix of the live one's digits
      File prefixSeed = createUnregistered("s" + live / 10 + "_1.tdat");
      writeLease(live, "1");
      // the lease of the other seed was seen and is expired
      writeLease(expired, "1");
      assertTrue(SwapSeedLeases.isAlive(cacheDir, expired));
      now.addAndGet(SwapSeedLeases.getTimeout());

      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);

      assertTrue(mine.exists());
      assertFalse(other.exists(), "the lease of another seed protected a file");
      assertFalse(noSeed.exists(), "a lease protected a file without a seed");
      assertFalse(plain.exists(), "a lease protected a file that is not a swap file");
      assertFalse(prefixSeed.exists(), "a lease protected the seed that has its digits as prefix");
   }

   // A6, the clean up of the cache directory keeps the live leases, and their directory
   @ParameterizedTest
   @EnumSource(Entry.class)
   void cacheCleanUpKeepsLiveLeasesAndTheirDirectory(Entry entry) throws Exception {
      Fixture fixture = new Fixture();
      writeLease(fixture.liveSeed, "10");
      long own = XSwapper.getSwapFileSeed(XSwapper.getSwapper().getPrefix() + "_s.tdat");
      assertTrue(leaseFile(own).exists());

      entry.ageForDataCache(fixture);
      run(entry);

      assertTrue(leaseDir().isDirectory(), entry + " deleted the lease directory");
      assertTrue(leaseFile(fixture.liveSeed).isFile(), entry + " deleted a live lease");
      assertTrue(leaseFile(own).isFile(), entry + " deleted the lease of this JVM");
   }

   // C3
   @Test
   void xswapperConstructsWhenLeaseDirectoryCannotBeCreated() throws Exception {
      Path lease = leaseDir().toPath();
      Path moved = Files.move(lease, new File(cacheDir, "__moved_leases__").toPath());

      try {
         // a regular file where the directory of the leases must be
         Files.createFile(lease);
         Thread.sleep(5L);
         XSwapper[] swapper = new XSwapper[1];
         assertDoesNotThrow(() -> swapper[0] = new XSwapper());
         usedSeeds.add(XSwapper.getSwapFileSeed(swapper[0].getPrefix() + "_s.tdat"));
         assertDoesNotThrow(SwapSeedLeases::refresh);
         assertDoesNotThrow(swapper[0]::stop);
         waitForSweepThreads();
      }
      finally {
         Files.deleteIfExists(lease);
         Files.move(moved, lease);
      }

      // the sweep of this JVM still works without the lease directory
      Fixture fixture = new Fixture();
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
      assertFalse(fixture.dead.exists());
   }

   // C4, a swap file written does not touch the lease
   @Test
   void swapOutDoesNotWriteTheLease() throws Exception {
      File lease = leaseFile(XSwapper.getSwapFileSeed(XSwapper.getSwapper().getPrefix() + "_s.tdat"));
      byte[] before = Files.readAllBytes(lease.toPath());
      long modified = lease.lastModified();

      for(int i = 0; i < 20; i++) {
         int[] values = new int[100];
         Arrays.fill(values, i);
         XIntFragment fragment = new XIntFragment(values);
         assertTrue(fragment.swap(), "fragment was not swapped");
         assertEquals(i, fragment.getSafely(5));
         fragment.dispose();
      }

      assertArrayEquals(before, Files.readAllBytes(lease.toPath()),
                        "the swap path wrote the lease");
      assertEquals(modified, lease.lastModified());
   }

   private void run(Entry entry) throws Exception {
      switch(entry) {
      case SWEEP_DEAD_SEED_SWAP_FILES:
         XSwapper.getSwapper().sweepDeadSeedSwapFiles(cacheDir);
         break;
      case STARTUP_SWEEP:
         Thread.sleep(5L);
         XSwapper swapper = new XSwapper();

         try {
            waitForSweepThreads();
         }
         finally {
            swapper.stop();
            usedSeeds.add(XSwapper.getSwapFileSeed(swapper.getPrefix() + "_s.tdat"));
         }

         break;
      case CLEAR_CACHE_FILES:
         FileSystemService.getInstance().clearCacheFiles(null);
         waitForSweepThreads();
         break;
      case CLEAR_DATA_CACHE:
         Method clearDataCache = ClearOldCacheFilesRunnable.class
            .getDeclaredMethod("clearDataCache");
         clearDataCache.setAccessible(true);
         clearDataCache.invoke(new ClearOldCacheFilesRunnable());
         break;
      }
   }

   /**
    * The four places that sweep the cache directory. The startup sweep is the one that the
    * constructor of a swapper starts, SwapFileSweepRunnable calls the first one.
    */
   enum Entry {
      SWEEP_DEAD_SEED_SWAP_FILES,
      STARTUP_SWEEP,
      CLEAR_CACHE_FILES,
      CLEAR_DATA_CACHE;

      // clearDataCache goes from the oldest file and stops after one delete when the disk is not
      // low, so the kept files must be the oldest, and the one that is deleted the newest
      void ageForDataCache(Fixture fixture) {
         if(this == CLEAR_DATA_CACHE) {
            long now = System.currentTimeMillis();
            assertTrue(fixture.live.setLastModified(now - 1000 * 86400000L));
            assertTrue(fixture.dead.setLastModified(now - 997 * 86400000L));
         }
      }
   }

   private long nextSeed() {
      long seed = nextSeed.getAndAdd(10);
      usedSeeds.add(seed);
      return seed;
   }

   private File leaseDir() {
      return new File(cacheDir, "__persistent__swap-seeds");
   }

   private File leaseFile(long seed) {
      return new File(leaseDir(), "swap-seed-" + seed + ".lease");
   }

   // the lease of another JVM, in the documented format
   private void writeLease(long seed, String counter) throws Exception {
      Files.createDirectories(leaseDir().toPath());
      Files.writeString(leaseFile(seed).toPath(), counter + "\nnode=" + DEAD_NODE + "\n",
                        StandardCharsets.UTF_8);
   }

   private File createRegistered(String name) throws Exception {
      File file = createUnregistered(name);
      Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);
      map.put(file.getAbsolutePath(), 1);
      return file;
   }

   private File createUnregistered(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      // older than any grace period of the sweeps
      assertTrue(file.setLastModified(System.currentTimeMillis() - 2 * 3600000L));
      created.add(file);
      return file;
   }

   private static void waitUntilDeleted(File file) throws InterruptedException {
      long end = System.currentTimeMillis() + 30000L;

      while(file.exists() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      waitForSweepThreads();
      assertFalse(file.exists(), "sweep did not delete " + file.getName() + " in 30 s");
   }

   // the sweeps of the startup and of the clean up run in a background thread
   private static void waitForSweepThreads() throws InterruptedException {
      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         if(XSwapper.CACHE_SWEEP_THREAD.equals(thread.getName()) ||
            FileSystemService.CLEAR_CACHE_FILES_THREAD.equals(thread.getName()))
         {
            thread.join(30000L);
            assertFalse(thread.isAlive(), "sweep thread did not finish");
         }
      }

      // the sweep holds the lock until it has removed the stale entries
      Lock lock = Cluster.getInstance().getLock(XSwapper.SWAP_FILE_MAP_LOCK);
      lock.lock();
      lock.unlock();
   }

   /**
    * A registered file of a seed whose owner is not in the topology, and one of a seed that has
    * no lease, both past every age gate. The test decides if the first seed has a lease.
    */
   private final class Fixture {
      Fixture() throws Exception {
         Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
         liveSeed = nextSeed();
         deadSeed = nextSeed();
         seeds.put(liveSeed, DEAD_NODE);
         seeds.put(deadSeed, DEAD_NODE);
         live = createRegistered("s" + liveSeed + "_1_s.tdat");
         dead = createRegistered("s" + deadSeed + "_1_s.tdat");
      }

      void assertEntriesKept() {
         Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
         Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);
         assertTrue(seeds.containsKey(liveSeed), "the seed of a live lease was removed");
         assertTrue(map.containsKey(live.getAbsolutePath()), "the entry of a live lease was removed");
         assertTrue(leaseFile(liveSeed).exists(), "the live lease was deleted");
      }

      final long liveSeed;
      final long deadSeed;
      final File live;
      final File dead;
   }

   private final AtomicLong now = new AtomicLong();
   private final List<File> created = new ArrayList<>();
   private final List<Long> usedSeeds = new ArrayList<>();
   private File cacheDir;
   private static final AtomicLong nextSeed = new AtomicLong(79134_000_000L);
   private static final String DEAD_NODE = "dead-node-79134";
   private static final String OTHER_LIVE_NODE = "other-live-node-79134";

   @Configuration
   static class TestClusterConfiguration {
      // replaces the cluster of BaseTestConfiguration with one that has another live node
      @Bean
      public Cluster cluster() {
         return new MockCluster() {
            @Override
            public Set<String> getClusterNodeIds() {
               return Set.of(getLocalNodeId(), OTHER_LIVE_NODE);
            }
         };
      }
   }
}
