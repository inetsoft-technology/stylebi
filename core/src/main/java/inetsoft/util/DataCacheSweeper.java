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

import inetsoft.util.swap.XSwapper;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ConcurrentModificationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Lazy
public class DataCacheSweeper {
   public DataCacheSweeper(XSwapper swapper) {
      this.swapper = swapper;
   }

   public static DataCacheSweeper getInstance() {
      return ConfigurationContext.getContext().getSpringBean(DataCacheSweeper.class);
   }

   @Scheduled(initialDelay = 10_000L, fixedDelay = 10_000L)
   public void sweep() {
      long now = System.currentTimeMillis();

      try {
         DataCache<?, ?>[] caches;

         synchronized(cacheSet) {
            caches = cacheSet.toArray(new DataCache<?, ?>[0]);
         }

         for(DataCache<?, ?> cache : caches) {
            if(now > cache.lastCheck + 60000) {
               cache.checkTimeout();
            }
         }
      }
      catch(ConcurrentModificationException exc) {
         // ignore
      }

      AtomicLong minage = new AtomicLong(4000L);
      // only entries evicted for critical memory, not the timed out ones, are worth a full gc
      AtomicBoolean evicted = new AtomicBoolean(false);

      try {
         Awaitility.await()
            .pollDelay(200L, TimeUnit.MILLISECONDS)
            .until(() -> sweep(evicted, minage));
      }
      finally {
         // call gc() so table lens will be disposed, and the swap files
         // cleaned up immediately. the swapper throttles it.
         if(evicted.get()) {
            swapper.requestGC();
         }
      }
   }

   void addCache(DataCache<?, ?> cache) {
      synchronized(cacheSet) {
         cacheSet.add(cache);
      }
   }

   private boolean sweep(AtomicBoolean evicted, AtomicLong minage) {
      if(swapper.getMemoryState() > XSwapper.CRITICAL_MEM) {
         return true;
      }

      try {
         int nremain = 0;
         DataCache<?, ?>[] caches;

         synchronized(cacheSet) {
            caches = cacheSet.toArray(new DataCache<?, ?>[0]);
         }

         for(DataCache<?, ?> cache : caches) {
            evicted.set(cache.sweep(minage.get()) || evicted.get());
            nremain += cache.cachemap.size();
         }

         if(nremain <= 0 || minage.get() <= 500) {
            return true;
         }

         minage.set(minage.get() - 500);
      }
      catch(Exception exc) {
         LOG.warn("Failed to clean up cache", exc);
      }

      return swapper.getMemoryState() > XSwapper.CRITICAL_MEM;
   }

   private final XSwapper swapper;
   private final DataCache.CacheSet cacheSet = new DataCache.CacheSet();

   private static final Logger LOG = LoggerFactory.getLogger(DataCacheSweeper.class);
}
