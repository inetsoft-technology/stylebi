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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.*;

public class DelayedMergeMap<K, V> extends AbstractMap<K, V> {
   public DelayedMergeMap(Map<K, V> map1, Map<K, V> map2) {
      this.map1 = map1;
      this.map2 = map2;
      this.mergedMap = null;
   }

   private synchronized void createMergedMap() {
      if(mergedMap == null) {
         mergedMap = new Object2ObjectOpenHashMap<>();
         mergedMap.putAll(map1);
         mergedMap.putAll(map2);
      }
   }

   @Override
   public V get(Object key) {
      if(mergedMap == null) {
         V value = map2.get(key);
         return value != null ? value : map1.get(key);
      }

      return mergedMap.get(key);
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.entrySet();
   }

   @Override
   public V put(K key, V value) {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.put(key, value);
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      if(this.mergedMap == null) {
         createMergedMap();
      }

      if(m instanceof DelayedMergeMap) {
         DelayedMergeMap<K, V> delayedMergeMap = (DelayedMergeMap<K, V>) m;

         if(delayedMergeMap.mergedMap != null) {
            this.mergedMap.putAll(delayedMergeMap.mergedMap);
         }
         else {
            this.mergedMap.putAll(delayedMergeMap.map1);
            this.mergedMap.putAll(delayedMergeMap.map2);
         }
      }
      else {
         this.mergedMap.putAll(m);
      }
   }

   @Override
   public DelayedMergeMap<K, V> clone() {
      DelayedMergeMap<K, V> c = new DelayedMergeMap<>(this.map1, this.map2);

      if(this.mergedMap != null) {
         c.mergedMap = this.mergedMap.clone();
      }

      return c;
   }

   @Override
   public boolean equals(Object other) {
      if(other instanceof DelayedMergeMap) {
         return getMergedMap().equals(((DelayedMergeMap<?, ?>) other).getMergedMap());
      }

      return false;
   }

   @Override
   public Set<K> keySet() {
      Set<K> keys = new HashSet<>();

      if(mergedMap != null) {
         return mergedMap.keySet();
      }

      if(map1 instanceof DelayedMergeMap) {
         DelayedMergeMap<K, V> dm1 = (DelayedMergeMap<K, V>) map1;
         keys.addAll(dm1.keySet());
      }
      else {
         keys.addAll(map1.keySet());
      }

      if(map2 instanceof DelayedMergeMap) {
         DelayedMergeMap<K, V> dm2 = (DelayedMergeMap<K, V>) map2;
         keys.addAll(dm2.keySet());
      }
      else {
         keys.addAll(map2.keySet());
      }

      return keys;
   }

   @Override
   public Collection<V> values() {
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
   public V remove(Object key) {
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
   public int hashCode() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap.hashCode();
   }

   public Map<K, V> getMap1() {
      return map1;
   }

   public Map<K, V> getMap2() {
      return map2;
   }

   private Object2ObjectOpenHashMap<K, V> getMergedMap() {
      if(mergedMap == null) {
         createMergedMap();
      }

      return mergedMap;
   }

   private Map<K, V> map1;
   private Map<K, V> map2;
   private Object2ObjectOpenHashMap<K, V> mergedMap;
}
