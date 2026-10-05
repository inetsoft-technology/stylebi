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

package inetsoft.util;

import inetsoft.test.*;
import inetsoft.util.swap.XSwapper;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77591: evicted cache entries are only cleaned up (heap freed, swap files deleted) after a
 * garbage collection, so the sweeper requests the swapper's throttled collection after a
 * critical-memory sweep evicted something, but never because of timeout expiry alone. The
 * context is only needed because the DataCache constructor registers with the (mocked)
 * DataCacheSweeper bean; each test sweeps with its own sweeper and a mocked XSwapper.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("slow")
class DataCacheSweeperTest {
   @Test
   void requestsGCAfterCriticalEviction() {
      XSwapper swapper = mock(XSwapper.class);
      when(swapper.getMemoryState()).thenReturn(XSwapper.CRITICAL_MEM);
      DataCacheSweeper sweeper = new DataCacheSweeper(swapper);
      DataCache<String, String> cache = new DataCache<>();
      cache.put("a", "1");
      sweeper.addCache(cache);

      sweeper.sweep();

      assertTrue(cache.cachemap.isEmpty(), "entry was not evicted");
      verify(swapper, times(1)).requestGC();
   }

   @Test
   void noGCForTimeoutExpiryOnly() throws Exception {
      XSwapper swapper = mock(XSwapper.class);
      when(swapper.getMemoryState()).thenReturn(XSwapper.GOOD_MEM);
      DataCacheSweeper sweeper = new DataCacheSweeper(swapper);
      DataCache<String, String> cache = new DataCache<>(20, 1L);
      cache.put("a", "1");
      sweeper.addCache(cache);
      Thread.sleep(10L);

      sweeper.sweep();

      assertTrue(cache.cachemap.isEmpty(), "expired entry was not removed");
      verify(swapper, never()).requestGC();
   }

   @Test
   void requestsGCWhenEvictionLoopTimesOut() {
      XSwapper swapper = mock(XSwapper.class);
      when(swapper.getMemoryState()).thenReturn(XSwapper.CRITICAL_MEM);
      DataCacheSweeper sweeper = new DataCacheSweeper(swapper);
      // evicts on the first pass but leaves an entry, then fails on every pass, so the loop
      // never finishes
      DataCache<String, String> cache = new DataCache<>() {
         @Override
         boolean sweep(long minage) {
            if(!swept) {
               swept = true;
               boolean evicted = super.sweep(-1L);
               put("b", "2");
               return evicted;
            }

            throw new IllegalStateException("sweep failed");
         }

         private boolean swept;
      };
      cache.put("a", "1");
      sweeper.addCache(cache);

      assertThrows(ConditionTimeoutException.class, sweeper::sweep);
      verify(swapper, times(1)).requestGC();
   }
}
