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
package inetsoft.util.algo;

import java.util.*;

/**
 * A bidirectional map.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class BidiMap<K, V> implements Map<K, V> {
   /**
    * Creates a new instance of BidiMap. By default, the map is not sorted.
    */
   public BidiMap() {
      this(false);
   }

   /**
    * Creates a new instance of BidiMap. If the map is sorted, it will be backed
    * by TreeMap objects. Unsorted maps will be backed by HashMap objects.
    * Sorted maps will use the natural sort order of the keys and values.
    *
    * @param sorted <code>true</code> if the backing maps should be sorted;
    *               <code>false</code> otherwise.
    */
   public BidiMap(boolean sorted) {
      if(sorted) {
         keyMap = new TreeMap();
         valueMap = new TreeMap();
      }
      else {
         keyMap = new HashMap();
         valueMap = new HashMap();
      }
   }

   /**
    * Creates a new instance of BidiMap. The map will be backed by TreeMap
    * objects that use the specified comparators to sort the keys and values.
    *
    * @param keyComparator the comparator for the key entries.
    * @param valueComparator the comparator for the value entries.
    */
   public BidiMap(Comparator keyComparator, Comparator valueComparator) {
      keyMap = new TreeMap(keyComparator);
      valueMap = new TreeMap(valueComparator);
   }

   /**
    * Returns the number of key-value mappings in this map. If the map contains
    * more than <code>Integer.MAX_VALUE</code> elements, returns
    * <code>Integer.MAX_VALUE</code>.
    *
    * @return the number of key-value mappings in this map.
    */
   @Override
   public int size() {
      return keyMap.size();
   }

   /**
    * Returns <code>true</code> if this map contains no key-value mappings.
    *
    * @return <code>true</code> if this map contains no key-value mappings.
    */
   @Override
   public boolean isEmpty() {
      return keyMap.isEmpty();
   }

   /**
    * Returns <code>true</code> if this map contains a mapping for the specified
    * key.
    *
    * @param key key whose presence in this map is to be tested.
    *
    * @return <code>true</code> if this map contains a mapping for the
    *         specified key.
    *
    * @throws NullPointerException if the key is <code>null</code> and this map
    *                              does not not permit <code>null</code> keys.
    */
   @Override
   public boolean containsKey(Object key) {
      return keyMap.containsKey(key);
   }

   /**
    * Returns <code>true</code> if this map contains a mapping for the specified
    * value.
    *
    * @param value value whose presence in this map is to be tested.
    *
    * @return <code>true</code> if this map maps one or more keys to the
    *         specified value.
    *
    * @throws NullPointerException if the value is <code>null</code> and this
    *                              map does not not permit <code>null</code>
    *                              values.
    */
   @Override
   public boolean containsValue(Object value) {
      return valueMap.containsKey(value);
   }

   /**
    * Returns the value to which this map maps the specified key. Returns
    * <code>null</code> if the map contains no mapping for this key.
    *
    * @param key key whose associated value is to be returned.
    *
    * @return the value to which this map maps the specified key, or
    *         <code>null</code> if the map contains no mapping for this key.
    *
    * @throws NullPointerException if the key is <code>null</code> and this map
    *                              does not permit <code>null</code> keys.
    */
   @Override
   public V get(Object key) {
      return keyMap.get(key);
   }

   /**
    * Returns the key to which this map maps the specified value. Returns
    * <code>null</code> if the map contains no mapping for this value.
    *
    * @param value <<Description>>
    *
    * @return value whose associated key is to be returned.
    *
    * @throws NullPointerException if the value is <code>null</code> and this
    *                              map does not not permit <code>null</code>
    *                              values.
    */
   public K getKey(V value) {
      return valueMap.get(value);
   }

   /**
    * Associates the specified value with the specified key in this map. If the
    * map previously contained a mapping for this key, the old value is replaced
    * by the specified value.
    *
    * @param key key with which the specified value is to be associated.
    * @param value value to be associated with the specified key.
    *
    * @return previous value associated with specified key, or <code>null</code>
    *         if there was no mapping for key.
    *
    * @throws NullPointerException if this map does not permit <code>null</code>
    *                              keys or values, and the specified key or
    *                              value is <code>null</code>.
    */
   @Override
   public V put(K key, V value) {
      V old = get(key);

      keyMap.put(key, value);
      valueMap.put(value, key);

      return old;
   }

   /**
    * Removes the mapping for this key from this map if it is present.
    *
    * @param key key whose mapping is to be removed from the map.
    *
    * @return previous value associated with specified key, or <code>null</code>
    *         if there was no mapping for key.
    *
    * @throws NullPointerException if the key is <code>null</code> and this map
    *                              does not not permit <code>null</code> keys.
    */
   @Override
   public V remove(Object key) {
      V value = keyMap.remove(key);

      if(value != null) {
         valueMap.remove(value);
      }

      return value;
   }

   /**
    * Removes the mapping for this value from this map if it is present.
    *
    * @param value value whose mapping is to be removed from the map.
    *
    * @return previous key associated with specified value, or <code>null</code>
    *         if there was no mapping for value.
    */
   public Object removeValue(Object value) {
      Object key = valueMap.remove(value);

      if(key != null) {
         keyMap.remove(key);
      }

      return key;
   }

   /**
    * Copies all of the mappings from the specified map to this map.
    *
    * @param t mappings to be stored in this map.
    *
    * @throws NullPointerException the specified map is <code>null</code>, or if
    *                              this map does not permit <code>null</code>
    *                              keys or values, and the specified map
    *                              contains <code>null</code> keys or values.
    */
   @Override
   public void putAll(Map<? extends K, ? extends V> t) {
      for(K key : t.keySet()) {
         V value = t.get(key);
         put(key, value);
      }
   }

   /**
    * Removes all mappings from this map.
    */
   @Override
   public void clear() {
      keyMap.clear();
      valueMap.clear();
   }

   /**
    * Returns a set view of the keys contained in this map.
    *
    * @return a set view of the keys contained in this map.
    */
   @Override
   public Set<K> keySet() {
      return keyMap.keySet();
   }

   /**
    * Returns a collection view of the values contained in this map.
    *
    * @return a collection view of the values contained in this map.
    */
   @Override
   public Collection<V> values() {
      return keyMap.values();
   }

   /**
    * Returns a set view of the mappings contained in this map. Each element in
    * the returned set is a <code>Map.Entry</code>.
    *
    * @return a set view of the mappings contained in this map.
    */
   @Override
   public Set<Map.Entry<K, V>> entrySet() {
      return keyMap.entrySet();
   }

   /**
    * Compares the specified object with this map for equality. Returns
    * <code>true</code> if the given object is also a map and the two Maps
    * represent the same mappings.
    *
    * @param o object to be compared for equality with this map.
    *
    * @return <code>true</code> if the specified object is equal to this map.
    */
   public boolean equals(Object o) {
      return keyMap.equals(o);
   }

   /**
    * Returns the hash code value for this map. The hash code of a map is
    * defined to be the sum of the hashCodes of each entry in the map's entrySet
    * view.
    *
    * @return the hash code value for this map.
    */
   public int hashCode() {
      return keyMap.hashCode();
   }

   private Map<K, V> keyMap = null;
   private Map<V, K> valueMap = null;
}
