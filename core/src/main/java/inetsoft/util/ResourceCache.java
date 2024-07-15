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
package inetsoft.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ResourceCache caches resources.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public abstract class ResourceCache<K, V> {
   /**
    * Create a resource cache.
    */
   public ResourceCache() {
      this(100);
   }

   /**
    * Create a resource cache with maximum size.
    * @param max the specified max size, a value less than or equal to zero
    * means no limit.
    */
   public ResourceCache(int max) {
      // don't hold too long as the entire report and data will be held in
      // memory for worksheet engine
      this(max, 1000L * 60 * 60);
   }

   /**
    * Create a resource cache with maximum size and timeout.
    * @param max the specified max size, a value less than or equal to zero
    * means no limit.
    * @param timeout the specified timeout, a value less than or equal to zero
    * means no timeout.
    */
   public ResourceCache(int max, long timeout) {
      this.max = max;
      this.timeout = timeout;
      this.map = new ConcurrentHashMap<>();

      lrtime = System.currentTimeMillis();
      lptime = System.currentTimeMillis();
   }

   /**
    * Create a resource.
    */
   protected abstract V create(K key) throws Exception;

   /**
    * Get a resource.
    */
   public V get(K key) throws Exception {
      ValueEntry<K, V> entry = map.get(key);
      long ctime = System.currentTimeMillis();
      boolean changed = false;

      if(entry == null) {
         V val = create(key);
         entry = new ValueEntry<>(key, val);
         checkTimeOut();
         checkSize();
         final ValueEntry<K, V> oldValue = map.putIfAbsent(key, entry);

         // There was a concurrent add to the map, don't add new value, clean up new value.
         if(oldValue != null) {
            processRemoved(val);
            entry = oldValue;
         }
         else {
            changed = true;
         }
      }
      // @by fredaan, fix bug1427264758182, change 60000 to 6000, to make sure
      // the repository tree(so do other data logic) can be refreshed timely.
      else if(ctime - lptime > 6000) {
         changed = true;
      }

      entry.date = ctime;

      if(changed) {
         processChanged();
      }

      return entry.val;
   }

   /**
    * Check if the resource already exists.
    */
   public final boolean contains(K key) {
      return map.containsKey(key);
   }

   /**
    * Remove a resource.
    */
   public final V remove(K key) {
      boolean changed = checkTimeOut();
      ValueEntry<K, V> entry = map.remove(key);
      changed = entry != null || changed;

      if(changed) {
         processChanged();
      }

      return (entry != null) ? entry.val : null;
   }

   /**
    * Remove all resources.
    */
   public final void clear() {
      boolean changed = map.size() > 0;
      map.clear();

      if(changed) {
         processChanged();
      }
   }

   /**
    * Process changed.
    */
   protected void processChanged() {
      lptime = System.currentTimeMillis();
   }

   /**
    * This method is called when an item is removed from the cache. It is not
    * called if the item is removed by an explicit call to the remove method.
    */
   protected void processRemoved(V value) {
   }

   /**
    * Get the size.
    */
   public final int size() {
      return map.size();
   }

   /**
    * Check size.
    */
   protected final boolean checkSize() {
      boolean changed = false;

      while(max > 0 && map.size() >= max) {
         Iterator<ValueEntry<K, V>> values = map.values().iterator();
         ValueEntry<K, V> entry = null;

         while(values.hasNext()) {
            ValueEntry<K, V> tentry = values.next();

            if(entry == null || tentry.date < entry.date) {
               entry = tentry;
            }
         }

         if(entry != null) {
            map.remove(entry.key);
            processRemoved(entry.val);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Check timeout.
    */
   protected boolean checkTimeOut() {
      boolean changed = false;

      if(timeout <= 0) {
         return changed;
      }

      long ctime = System.currentTimeMillis();

      if(ctime - lrtime < (timeout / 3)) {
         return changed;
      }

      lrtime = ctime;
      List<ValueEntry<K, V>> list = new ArrayList<>(map.values());

      for(ValueEntry<K, V> tentry : list) {
         if(ctime - tentry.date >= timeout) {
            map.remove(tentry.key);
            processRemoved(tentry.val);
            changed = true;
         }
      }

      return changed;
   }

   /**
    * Value entry contains value and last access time.
    */
   protected static final class ValueEntry<K, V> {
      ValueEntry(K key, V val) {
         this.key = key;
         this.val = val;
      }

      public final K key;
      public final V val;
      public long date;
   }

   protected ConcurrentMap<K, ValueEntry<K, V>> map;

   private int max;
   private long timeout;
   private long lrtime;
   private long lptime;
}
