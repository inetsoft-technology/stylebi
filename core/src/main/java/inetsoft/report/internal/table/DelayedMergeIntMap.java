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

package inetsoft.report.internal.table;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.*;

import java.util.Map;

public class DelayedMergeIntMap<K> extends AbstractObject2IntMap<K> {
   public DelayedMergeIntMap() {
      this.map1 = new Object2IntOpenHashMap<>();
      this.map2 = new Object2IntOpenHashMap<>();
   }

   public DelayedMergeIntMap(Object2IntOpenHashMap<K> map1, Object2IntOpenHashMap<K> map2) {
      this.map1 = map1;
      this.map2 = map2;
      this.mergedMap = null;
   }

   public DelayedMergeIntMap(DelayedMergeIntMap<K> map1, DelayedMergeIntMap<K> map2) {
      this.map1 = map1.getMergedMap();
      this.map2 = map2.getMergedMap();
      this.mergedMap = null;
   }

   private synchronized void createMergedMap() {
      if(mergedMap == null) {
         mergedMap = new Object2IntOpenHashMap<>();
         mergedMap.putAll(map1);
         mergedMap.putAll(map2);
      }
   }

   @Override
   public int getInt(Object key) {
      if(mergedMap == null) {
         if(map2.containsKey(key)) {
            return map2.getInt(key);
         }
         return map1.getInt(key);
      }

      return mergedMap.getInt(key);
   }

   @Override
   public int put(K key, int value) {
      if(mergedMap == null) {
         createMergedMap();
      }
      return mergedMap.put(key, value);
   }

   @Override
   public ObjectSet<Map.Entry<K, Integer>> entrySet() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.entrySet();
   }

   @Override
   public void putAll(Map<? extends K, ? extends Integer> m) {
      if(this.mergedMap == null) {
         createMergedMap();
      }

      this.mergedMap.putAll(m);
   }

   @Override
   public DelayedMergeIntMap<K> clone() {
      DelayedMergeIntMap<K> c = new DelayedMergeIntMap<>(this.map1, this.map2);

      if(this.mergedMap != null) {
         c.mergedMap = this.mergedMap.clone();
      }

      return c;
   }

   @Override
   public boolean equals(Object other) {
      if(other instanceof DelayedMergeIntMap) {
         return mergedMap == null ? map1.equals(((DelayedMergeIntMap<?>) other).getMap1()) &&
            map2.equals(((DelayedMergeIntMap<?>) other).getMap2()) :
            mergedMap.equals(((DelayedMergeIntMap<?>) other).getMergedMap());
      }

      return false;
   }

   @Override
   public ObjectSet<K> keySet() {
      if(mergedMap == null) {
         createMergedMap();
      }
      return mergedMap.keySet();
   }

   @Override
   public IntCollection values() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.values();
   }

   @Override
   public int size() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.size();
   }

   @Override
   public boolean isEmpty() {
      return mergedMap == null ? map1.isEmpty() && map2.isEmpty() : mergedMap.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return mergedMap == null ? map1.containsKey(key) || map2.containsKey(key) :
         mergedMap.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      return mergedMap == null ? map1.containsValue(value) || map2.containsValue(value) :
         mergedMap.containsValue(value);
   }

   @Override
   public Integer remove(Object key) {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.remove(key);
   }

   @Override
   public void clear() {
      if(mergedMap != null) {
         mergedMap.clear();
      }

      map1.clear();
      map2.clear();
   }

   @Override
   public ObjectSet<Entry<K>> object2IntEntrySet() {
      if(mergedMap == null) {
         createMergedMap();
      }
      return mergedMap.object2IntEntrySet();
   }

   @Override
   public int hashCode() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.hashCode();
   }

   public Object2IntOpenHashMap<K> getMergedMap() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap;
   }

   public Object2IntOpenHashMap<K> getMap1() {
      return map1;
   }

   public Object2IntOpenHashMap<K> getMap2() {
      return map2;
   }

   private final Object2IntOpenHashMap<K> map1;
   private final Object2IntOpenHashMap<K> map2;
   private Object2IntOpenHashMap<K> mergedMap;
}