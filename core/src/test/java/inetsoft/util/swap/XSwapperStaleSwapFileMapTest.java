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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;

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

   private static void waitUntilDeleted(File file) throws InterruptedException {
      // the sweeps run in a background thread
      long end = System.currentTimeMillis() + 30000L;

      while(file.exists() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }
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
