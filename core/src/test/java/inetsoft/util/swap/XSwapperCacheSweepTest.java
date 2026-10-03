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

import inetsoft.test.*;
import inetsoft.util.ClearOldCacheFilesRunnable;
import inetsoft.util.FileSystemService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77600, the cache sweepers treated every file that is not in the swap file map as
 * orphaned, but most swappables (XIntFragment, XStringFragment, SelectionList, ...) never
 * register their files there. A cache clean-up during a running query deleted their live
 * swap files and the data silently read back as empty.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class XSwapperCacheSweepTest {
   @Test
   void isOwnSwapFileMatchesOnlyThisSwapperPrefix() {
      XSwapper swapper = XSwapper.getSwapper();
      String prefix = swapper.getPrefix();
      String seedPrefix = prefix.substring(0, prefix.lastIndexOf('_') + 1);

      assertTrue(swapper.isOwnSwapFile(prefix + ".tdat"));
      assertTrue(swapper.isOwnSwapFile(prefix + "_slist.swap"));
      assertFalse(swapper.isOwnSwapFile("s1_1.tdat"));
      // the trailing '_' keeps a longer seed from matching
      assertFalse(swapper.isOwnSwapFile(seedPrefix.substring(0, seedPrefix.length() - 1) +
                                           "0_1.tdat"));
      assertFalse(swapper.isOwnSwapFile(null));
   }

   @Test
   void clearCacheFilesKeepsLiveSwapFilesOfThisJvm() throws Exception {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      int[] values = new int[100];

      for(int i = 0; i < values.length; i++) {
         values[i] = 1000 + i;
      }

      XIntFragment fragment = new XIntFragment(values);
      assertTrue(fragment.swap(), "fragment was not swapped");
      File liveFile = fileSystemService.getCacheFile(fragment.prefix + ".tdat");
      assertTrue(liveFile.exists(), "swap file was not written");

      File ownOther = createCacheFile(XSwapper.getSwapper().getPrefix() + "_slist.swap");
      File foreignData = createCacheFile("s1_1.tdat");
      File foreignOther = createCacheFile("s1_2_slist.swap");
      // Bug #77627, outside the registration grace period so the sweep is not
      // just waiting it out
      assertTrue(foreignData.setLastModified(
         System.currentTimeMillis() - XSwapper.SWAP_FILE_GRACE_PERIOD - 1000L));

      fileSystemService.clearCacheFiles(null);

      // the clean-up runs in a background thread
      long end = System.currentTimeMillis() + 30000L;

      while((foreignData.exists() || foreignOther.exists()) &&
         System.currentTimeMillis() < end)
      {
         Thread.sleep(50L);
      }

      assertFalse(foreignData.exists(), "foreign swap file was not cleaned");
      assertFalse(foreignOther.exists(), "foreign non-.tdat swap file was not cleaned");
      assertTrue(liveFile.exists(), "live swap file was deleted");
      assertTrue(ownOther.exists(), "own non-.tdat swap file was deleted");
      assertEquals(1005, fragment.getSafely(5));
      assertEquals(100, fragment.size());

      fragment.dispose();
      Files.deleteIfExists(ownOther.toPath());
   }

   @Test
   void clearCacheFilesKeepsRecentForeignSwapFileWithinGracePeriod() throws Exception {
      // Bug #77627, XSwapper.swapRemaining() writes a swap file to disk before it is
      // registered in the swap file map, so a freshly-written foreign file must survive a
      // sweep that runs inside that window, not just one that happens to match the map or
      // the prefix check.
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File recentForeign = createCacheFile("s4_1.tdat");
      // aged past the grace period, unrelated to the file under test: its deletion is the
      // signal that the background sweep actually ran, so a slow/loaded CI box can't make
      // this test pass just because the sweep hadn't reached recentForeign yet
      File agedForeign = createCacheFile("s5_1.tdat");
      assertTrue(agedForeign.setLastModified(
         System.currentTimeMillis() - XSwapper.SWAP_FILE_GRACE_PERIOD - 1000L));

      fileSystemService.clearCacheFiles(null);

      // the clean-up runs in a background thread
      long end = System.currentTimeMillis() + 30000L;

      while(agedForeign.exists() && System.currentTimeMillis() < end) {
         Thread.sleep(50L);
      }

      try {
         assertFalse(agedForeign.exists(), "aged foreign swap file was not cleaned");
         assertTrue(recentForeign.exists(),
            "recently-written foreign swap file was deleted within its registration grace period");
      }
      finally {
         Files.deleteIfExists(recentForeign.toPath());
         Files.deleteIfExists(agedForeign.toPath());
      }
   }

   @Test
   void clearDataCacheKeepsOldSwapFilesOfThisJvm() throws Exception {
      long now = System.currentTimeMillis();
      File own = createCacheFile(XSwapper.getSwapper().getPrefix() + ".tdat");
      File foreign = createCacheFile("s2_1.tdat");
      // far older than the 5 hour timeout and than any file left by other runs, own file
      // first so it is reached first (the sweep stops after one delete when not low on disk)
      assertTrue(own.setLastModified(now - 1000 * 86400000L));
      assertTrue(foreign.setLastModified(now - 999 * 86400000L));

      Method clearDataCache = ClearOldCacheFilesRunnable.class.getDeclaredMethod("clearDataCache");
      clearDataCache.setAccessible(true);
      clearDataCache.invoke(new ClearOldCacheFilesRunnable());

      assertTrue(own.exists(), "old swap file of this JVM was deleted");
      assertFalse(foreign.exists(), "old foreign swap file was not cleaned");

      Files.deleteIfExists(own.toPath());
   }

   private static File createCacheFile(String name) throws Exception {
      File file = FileSystemService.getInstance().getCacheFile(name);
      Files.write(file.toPath(), new byte[] { 1, 2, 3 });
      return file;
   }
}
