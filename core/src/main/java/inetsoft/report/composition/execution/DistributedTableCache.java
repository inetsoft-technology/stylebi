/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition.execution;

import com.google.common.util.concurrent.Striped;
import inetsoft.report.TableLens;
import inetsoft.util.SingletonManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

@SingletonManager.Singleton
public class DistributedTableCache {
   /**
    * Get the schedule viewsheet service.
    */
   public static DistributedTableCache getInstance() {
      return SingletonManager.getInstance(DistributedTableCache.class);
   }

   public DistributedTableCache() {
      this.store = new DistributedTableCacheStore();
      this.lensMap = new ConcurrentHashMap<>();
   }

   public boolean exists(DataKey key) {
      return store.exists(key.toString());
   }

   public TableLens get(DataKey key) {
      if(lensMap.containsKey(key)) {
         return lensMap.get(key);
      }

      if(!exists(key)) {
         return null;
      }

      Lock lock = locks.get(key);
      lock.lock();

      try {
         // check if table exists again once inside the lock as it could have been retrieved
         // from the store by another thread
         if(lensMap.containsKey(key)) {
            return lensMap.get(key);
         }

         TableLens lens = store.load(key.getValue());
         lensMap.put(key, lens);
         return lens;
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
      finally {
         lock.unlock();
      }
   }

   public void put(DataKey key, TableLens lens) {
      TableLens oldLens = lensMap.put(key, lens);

      if(oldLens == null || !oldLens.equals(lens)) {
         store.save(key.getValue(), lens);
      }
   }

   private final DistributedTableCacheStore store;
   private final ConcurrentHashMap<DataKey, TableLens> lensMap;
   private final Striped<Lock> locks = Striped.lazyWeakLock(256);
}