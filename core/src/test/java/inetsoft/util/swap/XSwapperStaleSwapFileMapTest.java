/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.mv.data.DimValueList;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.test.*;
import inetsoft.util.ClearOldCacheFilesRunnable;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78044, the entries of the swap file map are removed only by the JVM that added them and
 * the map lives as long as the cluster, so after a rolling restart the entries of the stopped
 * JVMs kept the cache sweeps from ever deleting their swap files. An entry must stop protecting
 * its file once the JVM that registered it is gone, but not while that JVM may still be running.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   XSwapperStaleSwapFileMapTest.TestClusterConfiguration.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class XSwapperStaleSwapFileMapTest {
   @AfterEach
   void cleanUp() throws Exception {
      Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);

      for(File file : created) {
         map.remove(file.getAbsolutePath());
         Files.deleteIfExists(file.toPath());
      }

      created.clear();
   }

   @Test
   void swapperRegistersItsSeedForTheLocalNode() {
      Cluster cluster = Cluster.getInstance();
      String prefix = XSwapper.getSwapper().getPrefix();
      Long seed = XSwapper.getSwapFileSeed(prefix + "_s.tdat");
      Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);

      assertNotNull(seed);
      assertEquals(cluster.getLocalNodeId(), seeds.get(seed));
   }

   @Test
   void registerSeedAddsEveryNodeOfTheSameSeedOnce() {
      Map<Long, String> seeds = new HashMap<>();

      assertTrue(XSwapper.registerSeed(seeds, 500L, "a"));
      assertTrue(XSwapper.registerSeed(seeds, 500L, "b"));
      assertTrue(XSwapper.registerSeed(seeds, 500L, "a"));
      assertEquals("a,b", seeds.get(500L));
   }

   @Test
   void getSwapFileSeedParsesSwapperPrefixOnly() {
      assertEquals(1791425321674L, XSwapper.getSwapFileSeed("s1791425321674_11_s.tdat"));
      assertEquals(12L, XSwapper.getSwapFileSeed("s12_3_0.tdat"));
      assertEquals(12L, XSwapper.getSwapFileSeed("s12_3_slist.swap"));
      assertNull(XSwapper.getSwapFileSeed("s_1.tdat"));
      assertNull(XSwapper.getSwapFileSeed("sx_1.tdat"));
      assertNull(XSwapper.getSwapFileSeed("table1s12_3_s.tdat"));
      assertNull(XSwapper.getSwapFileSeed(null));
   }

   @Test
   void clearCacheFilesDeletesRegisteredFilesOfDeadJvm() throws Exception {
      Fixture fixture = new Fixture(100L);

      FileSystemService.getInstance().clearCacheFiles(null);
      waitUntilDeleted(fixture.dead);

      fixture.assertSwept();
   }

   @Test
   void startupSweepDeletesRegisteredFilesOfDeadJvm() throws Exception {
      Fixture fixture = new Fixture(200L);
      XSwapper swapper = new XSwapper();

      try {
         waitUntilDeleted(fixture.dead);
      }
      finally {
         swapper.stop();
      }

      fixture.assertSwept();
   }

   @Test
   void clearDataCacheDeletesRegisteredFileOfDeadJvm() throws Exception {
      Fixture fixture = new Fixture(300L);
      long now = System.currentTimeMillis();
      // the sweep goes from the oldest file and stops after one delete when not low on disk,
      // so the kept files come first: a wrong delete of one of them leaves the dead one
      assertTrue(fixture.own.setLastModified(now - 1000 * 86400000L));
      assertTrue(fixture.liveOther.setLastModified(now - 999 * 86400000L));
      assertTrue(fixture.unknown.setLastModified(now - 998 * 86400000L));
      assertTrue(fixture.dead.setLastModified(now - 997 * 86400000L));

      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());

      assertFalse(fixture.dead.exists(), "registered file of a dead JVM was not deleted");
      assertTrue(fixture.own.exists(), "registered file of this JVM was deleted");
      assertTrue(fixture.liveOther.exists(), "registered file of another live JVM was deleted");
      assertTrue(fixture.unknown.exists(), "registered file of an unknown seed was deleted");
   }

   @Test
   void sweepsKeepRecentRegisteredFileOfDeadJvmWithinGracePeriod() throws Exception {
      Cluster cluster = Cluster.getInstance();
      Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(401L, DEAD_NODE);
      seeds.put(402L, DEAD_NODE);
      // the old file is deleted by the same sweep, so it marks when the sweep has run
      File marker = createRegistered("s401_1_s.tdat");
      // a JVM that was killed right after writing a swap file
      File recent = createRegistered("s402_1_s.tdat");
      assertTrue(recent.setLastModified(System.currentTimeMillis()));

      FileSystemService.getInstance().clearCacheFiles(null);
      waitUntilDeleted(marker);
      assertFalse(marker.exists(), "registered file of a dead JVM was not deleted");
      assertTrue(recent.exists(), "clean up deleted a file within its grace period");

      // the clean up removed the stale entries and seeds
      seeds.put(401L, DEAD_NODE);
      seeds.put(402L, DEAD_NODE);
      cluster.<String, Integer>getMap(XSwapper.SWAP_FILE_MAP).put(recent.getAbsolutePath(), 1);
      marker = createRegistered("s401_2_s.tdat");
      assertTrue(recent.setLastModified(System.currentTimeMillis()));
      XSwapper swapper = new XSwapper();

      try {
         waitUntilDeleted(marker);
      }
      finally {
         swapper.stop();
      }

      assertFalse(marker.exists(), "registered file of a dead JVM was not deleted");
      assertTrue(recent.exists(), "startup sweep deleted a file within its grace period");
   }

   /**
    * Bug #78245, the recurring sweep ({@link SwapFileSweepRunnable}, exercised here by calling
    * {@link XSwapper#sweepDeadSeedSwapFiles} directly as it does) must give a dead-seed file a
    * second chance once its grace period has elapsed, instead of only ever being evaluated once
    * by the one-shot startup sweep like {@link #sweepsKeepRecentRegisteredFileOfDeadJvmWithinGracePeriod}
    * demonstrates. This reproduces the gap the bug report describes: a file born in a dying JVM's
    * last seconds survives the first sweep (still within the grace period) but must not survive
    * forever -- a later sweep, once the file is old enough, must delete it without waiting for a
    * manual EM Clean Up or another restart.
    */
   @Test
   void recurringSweepDeletesADeadSeedFileOnceItsGracePeriodElapses() throws Exception {
      Cluster cluster = Cluster.getInstance();
      Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(501L, DEAD_NODE);
      // a JVM that was killed right after writing a swap file
      File recent = createRegistered("s501_1_s.tdat");
      assertTrue(recent.setLastModified(System.currentTimeMillis()));

      File dir = FileSystemService.getInstance().getFile(
         FileSystemService.getInstance().getCacheDirectory());

      // first look (e.g. the one-shot startup sweep, or the recurring sweep's first tick):
      // still inside the grace period, so the file must survive
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(dir);
      assertTrue(recent.exists(), "a file within its grace period was deleted");

      // simulate the clock moving past the grace period without waiting for it in real time
      assertTrue(recent.setLastModified(
         System.currentTimeMillis() - XSwapper.SWAP_FILE_GRACE_PERIOD - 1000L));

      // the recurring sweep's next tick re-evaluates the same, already dead seed
      XSwapper.getSwapper().sweepDeadSeedSwapFiles(dir);
      assertFalse(recent.exists(),
                   "the recurring sweep did not give the file a second, later chance to be swept");
   }

   /**
    * Bug #78245, the two tests above exercise {@link XSwapper#sweepDeadSeedSwapFiles} directly;
    * this one exercises the actual new production class, {@link SwapFileSweepRunnable}, that
    * {@code ServerLifecycleService} schedules with {@code TimedQueue} -- i.e. its {@code run()}
    * resolving the swap cache directory via {@link FileSystemService} and delegating to
    * {@link XSwapper#getSwapper()}, not just the extracted sweep method in isolation.
    */
   @Test
   void swapFileSweepRunnableSweepsADeadSeedFileThroughItsOwnRunMethod() throws Exception {
      Cluster cluster = Cluster.getInstance();
      Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(601L, DEAD_NODE);
      // a JVM that was killed right after writing a swap file
      File recent = createRegistered("s601_1_s.tdat");
      assertTrue(recent.setLastModified(System.currentTimeMillis()));

      SwapFileSweepRunnable runnable = new SwapFileSweepRunnable();
      assertTrue(runnable.isRecurring(), "the sweep must be scheduled to repeat");

      // first tick: still inside the grace period, so the file must survive
      runnable.run();
      assertTrue(recent.exists(), "a file within its grace period was deleted");

      // simulate the clock moving past the grace period without waiting for it in real time
      assertTrue(recent.setLastModified(
         System.currentTimeMillis() - XSwapper.SWAP_FILE_GRACE_PERIOD - 1000L));

      // second tick: the same confirmed-dead seed, now past the grace period
      runnable.run();
      assertFalse(recent.exists(),
         "SwapFileSweepRunnable.run() did not resolve the cache directory and sweep it");
   }

   @Test
   void unregisteredFilesAreKeptOnlyForLiveSeedsBySweepRunnable() throws Exception {
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(901L, OTHER_LIVE_NODE);
      seeds.put(902L, DEAD_NODE);
      seeds.put(904L, DEAD_NODE + "," + OTHER_LIVE_NODE);
      seeds.put(905L, Cluster.getInstance().getLocalNodeId());
      File live = createUnregistered("s901_1.tdat");
      File dead = createUnregistered("s902_1_dict.tdat");
      File unknown = createUnregistered("s903_1_0.tdat");
      File shared = createUnregistered("s904_1.tdat");
      File local = createUnregistered("s905_1.tdat");
      new SwapFileSweepRunnable().run();
      assertTrue(live.exists(), "unregistered file of a live foreign seed");
      assertFalse(dead.exists(), "unregistered file of a dead seed");
      assertFalse(unknown.exists(), "unregistered file of an unknown seed");
      assertTrue(shared.exists(), "unregistered file of a seed shared by a dead and a live node");
      assertTrue(local.exists(), "unregistered file of a seed owned by the local node");
   }

   @Test
   void unregisteredFilesOfLiveSeedsAreKeptByCleanUpAndDataCache() throws Exception {
      Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
      seeds.put(911L, OTHER_LIVE_NODE);
      seeds.put(912L, DEAD_NODE);
      File live = createUnregistered("s911_1.tdat");
      File liveNonTdat = createUnregistered("s911_2_slist.swap");
      File marker = createUnregistered("s912_1.tdat");
      FileSystemService.getInstance().clearCacheFiles(null);
      waitUntilDeleted(marker);
      assertTrue(live.exists(), "clearCacheFiles, live foreign seed");
      assertTrue(liveNonTdat.exists(), "non-tdat file of a live foreign seed");

      seeds.put(921L, OTHER_LIVE_NODE);
      long now = System.currentTimeMillis();
      File live2 = createUnregistered("s921_1.tdat");
      File unk = createUnregistered("s923_1.tdat");
      assertTrue(live2.setLastModified(now - 1000 * 86400000L));
      assertTrue(unk.setLastModified(now - 999 * 86400000L));
      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());
      assertTrue(live2.exists(), "clearDataCache, live foreign seed");
      assertFalse(unk.exists(), "clearDataCache, unknown seed");
   }

   /**
    * Bug #78368, the fragment files of a dimension value list are written while an MV is built
    * and read back only when its dictionary is written, so the recurring sweep must not delete
    * them in between, neither in this JVM nor in another JVM that shares the cache directory.
    */
   @Test
   void dimValueListFragmentsSurviveSweepOfThisAndOtherLiveJvm() throws Exception {
      DimValueList list = new DimValueList();
      File out = FileSystemService.getInstance().getCacheFile("b78368_dimvalue_out.dat");
      created.add(out);

      try {
         // one full fragment is written to the cache directory
         for(int i = 0; i < 10001; i++) {
            list.add("value" + i);
         }

         Field field = DimValueList.class.getDeclaredField("fragmentFiles");
         field.setAccessible(true);
         File fragment = ((List<File>) field.get(list)).get(0);
         created.add(fragment);
         assertTrue(XSwapper.getSwapper().isOwnSwapFile(fragment.getName()), fragment.getName());

         // the same name written by a running JVM of another live node
         Map<Long, String> seeds = Cluster.getInstance().getMap(XSwapper.SWAP_SEED_MAP);
         seeds.put(931L, OTHER_LIVE_NODE);
         File foreign = createUnregistered(
            "s931" + fragment.getName().substring(fragment.getName().indexOf('_')));
         assertTrue(fragment.setLastModified(System.currentTimeMillis() - 2 * 3600000L));

         new SwapFileSweepRunnable().run();

         assertTrue(fragment.exists(), "fragment of this JVM was deleted");
         assertTrue(foreign.exists(), "fragment of another live JVM was deleted");

         try(FileChannel channel = new RandomAccessFile(out, "rw").getChannel()) {
            list.write(channel);
         }
      }
      finally {
         list.dispose();
      }
   }

   // not in SWAP_FILE_MAP: a swappable that never registers its files, or one still inside the
   // batch window of Bug #77627
   private File createUnregistered(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      assertTrue(file.setLastModified(System.currentTimeMillis() - 2 * 3600000L));
      created.add(file);
      return file;
   }

   private static void waitUntilDeleted(File file) throws InterruptedException {
      // the sweeps run in a background thread
      long end = System.currentTimeMillis() + 30000L;

      while(file.exists() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      // the sweep holds the lock until it has removed the stale entries
      Lock lock = Cluster.getInstance().getLock(XSwapper.SWAP_FILE_MAP_LOCK);
      lock.lock();
      lock.unlock();

      // don't leave a sweep thread running after the test
      for(Thread thread : Thread.getAllStackTraces().keySet()) {
         if(XSwapper.CACHE_SWEEP_THREAD.equals(thread.getName()) ||
            FileSystemService.CLEAR_CACHE_FILES_THREAD.equals(thread.getName()))
         {
            thread.join(30000L);
            assertFalse(thread.isAlive(), "sweep thread did not finish");
         }
      }

      assertFalse(file.exists(), "sweep did not delete " + file.getName() + " in 30 s");
   }

   /**
    * One registered swap file of this JVM, of another live JVM, of a JVM that is gone and of
    * a JVM whose seed is not registered (e.g. of an older version), all past every age gate.
    */
   private final class Fixture {
      Fixture(long base) throws Exception {
         Cluster cluster = Cluster.getInstance();
         Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);
         long deadSeed = base + 1;
         long liveSeed = base + 2;
         long unknownSeed = base + 3;
         // a seed shared by a dead and a live node is alive
         long sharedSeed = base + 4;
         seeds.put(deadSeed, DEAD_NODE + "," + DEAD_NODE + "2");
         seeds.put(liveSeed, OTHER_LIVE_NODE);
         seeds.put(sharedSeed, DEAD_NODE + "," + OTHER_LIVE_NODE);
         // a JVM that had closed all its swap files before it stopped
         seeds.put(base + 5, DEAD_NODE);

         own = createRegistered(XSwapper.getSwapper().getPrefix() + "_s.tdat");
         liveOther = createRegistered("s" + liveSeed + "_1_s.tdat");
         shared = createRegistered("s" + sharedSeed + "_1_0.tdat");
         unknown = createRegistered("s" + unknownSeed + "_1.tdat");
         dead = createRegistered("s" + deadSeed + "_1_s.tdat");
         this.deadSeed = deadSeed;
      }

      void assertSwept() {
         Cluster cluster = Cluster.getInstance();
         Map<String, Integer> map = cluster.getMap(XSwapper.SWAP_FILE_MAP);
         Map<Long, String> seeds = cluster.getMap(XSwapper.SWAP_SEED_MAP);

         assertFalse(dead.exists(), "registered file of a dead JVM was not deleted");
         assertTrue(own.exists(), "registered file of this JVM was deleted");
         assertTrue(liveOther.exists(), "registered file of another live JVM was deleted");
         assertTrue(shared.exists(), "registered file of a seed with a live node was deleted");
         assertTrue(unknown.exists(), "registered file of an unknown seed was deleted");

         assertFalse(map.containsKey(dead.getAbsolutePath()), "stale entry was not removed");
         assertFalse(seeds.containsKey(deadSeed), "dead seed was not removed");
         assertFalse(seeds.containsKey(deadSeed + 4), "dead seed without files was not removed");
         assertTrue(seeds.containsKey(deadSeed + 1), "live seed was removed");
         assertTrue(seeds.containsKey(deadSeed + 3), "seed with a live node was removed");

         for(File file : new File[] { own, liveOther, shared, unknown }) {
            assertTrue(map.containsKey(file.getAbsolutePath()), "live entry was removed");
         }
      }

      final File own;
      final File liveOther;
      final File shared;
      final File unknown;
      final File dead;
      final long deadSeed;
   }

   private File createRegistered(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      // older than any grace period of the startup and clean-up sweeps
      assertTrue(file.setLastModified(System.currentTimeMillis() - 2 * 3600000L));
      Map<String, Integer> map = Cluster.getInstance().getMap(XSwapper.SWAP_FILE_MAP);
      map.put(file.getAbsolutePath(), 1);
      created.add(file);
      return file;
   }

   private final List<File> created = new ArrayList<>();
   private static final String DEAD_NODE = "dead-node";
   private static final String OTHER_LIVE_NODE = "other-live-node";

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
