/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of an ordered map. This is like hash table but the items
 * are kept in the same order as they are added. It is NOT efficient and
 * should only be used for small data collection.
 *
 * @author InetSoft Technology Corp.
 * @version 8.0
 */
public class OrderedMap<K, V> implements Map<K, V>, Cloneable, Serializable {
   public OrderedMap() {
      super();
   }

   public OrderedMap(Map<? extends K, ? extends V> map) {
      Iterator<? extends K> keys = map.keySet().iterator();

      while(keys.hasNext()) {
         K key = keys.next();
         V val = map.get(key);
         put(key, val);
      }
   }

   /**
    * Returns the number of keys in this map.
    *
    * @return the number of keys in this map.
    */
   @Override
   public final int size() {
      return map.size();
   }

   /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     *
     * @return <tt>true</tt> if this map contains no key-value mappings.
     */
    @Override
    public final boolean isEmpty() {
       return map.size() == 0;
    }

   /**
    * Returns an enumeration of the keys in this map.
    *
    * @return an enumeration of the keys in this map.
    */
   public final synchronized Enumeration<K> keys() {
      return Collections.enumeration(keys);
   }

   /**
    * Returns an enumeration of the values in this map. Use the Enumeration
    * methods on the returned object to fetch the elements sequentially.
    */
   public final synchronized Enumeration<V> elements() {
      return Collections.enumeration(values);
   }

   /**
    * Returns true if this Map maps one or more keys to this value.
    * <p>
    * Note that this method is identical in functionality to contains
    * (which predates the Map interface).
    *
    * @param value value whose presence in this map is to be tested.
    *
    * @return <tt>true</tt> if this map maps one or more keys to the
    *         specified value.
    */
   @Override
   public final boolean containsValue(Object value) {
      return map.containsValue(value == null ? NULL : value);
   }

   /**
    * Checks if the specified object is a key in this map.
    *
    * @param key possible key.
    *
    * @return <code>true</code> if and only if the specified object
    *         is a key in this map, as determined by the
    *         <tt>equals</tt> method; <code>false</code> otherwise.
    */
   @Override
   public final boolean containsKey(Object key) {
      return map.containsKey(key == null ? NULL : key);
   }

   /**
    * Returns the value to which the specified key is mapped in this map.
    *
    * @param key a key in the map.
    *
    * @return the value to which the key is mapped in this map;
    *         <code>null</code> if the key is not mapped to any value in
    *         this map.
    */
   @Override
   public final V get(Object key) {
      Object val = map.get(key == null ? NULL : key);
      return val == NULL ? null : (V) val;
   }

   /**
    * Maps the specified <code>key</code> to the specified <code>value</code> in
    * this map. Neither the key nor the value can be <code>null</code>.
    * <p>
    * The value can be retrieved by calling the <code>get</code> method with a
    * key that is equal to the original key.
    *
    * @param key   the map key.
    * @param value the value.
    *
    * @return the previous value of the specified key in this map, or
    *         <code>null</code> if it did not have one.
    */
   @Override
   public synchronized V put(K key, V value) {
      final Object mapKey = key == null ? NULL : key;

      if(map.containsKey(mapKey)) {
         for(int i = 0; i < keys.size(); i++) {
            if(keys.get(i).equals(key)) {
               values.set(i, value);
               map.put(mapKey, value == null ? NULL : value);
               return value;
            }
         }
      }
      else {
         keys.add(key);
         values.add(value);
         map.put(mapKey, value == null ? NULL : value);
      }

      return value;
   }

   /**
    * Put a key-value pair similar to put(), but move the entry to the end of the list if
    * it already exists.
    * @return previous value in the map.
    */
   public synchronized V append(K key, V value) {
      final Object mapKey = key == null ? NULL : key;

      if(map.containsKey(mapKey)) {
         for(int i = 0; i < keys.size(); i++) {
            if(Objects.equals(keys.get(i), key)) {
               values.remove(i);
               keys.remove(i);
               break;
            }
         }

         keys.add(key);
         values.add(value);
      }

      Object oval = map.put(mapKey, value == null ? NULL : value);
      return oval == NULL ? null : (V) oval;
   }

   /**
    * Puts the value at the specified position.
    *
    * @param idx the index of the key.
    * @param key the key object.
    *
    * @param value the value object.
    */
   public final synchronized void put(int idx, K key, V value) {
      final Object mapKey = key == null ? NULL : key;
      int index = !map.containsKey(mapKey) ? -1 : keys.indexOf(key);

      if(index == -1) {
         keys.add(idx, key);
         values.add(idx, value);
      }
      else {
         if(idx >= 0 && idx < keys.size()) {
            Object obj = keys.get(idx);
            map.remove(obj == null ? NULL : obj);
         }

         keys.set(idx, key);
         values.set(idx, value);
      }

      map.put(mapKey, value == null ? NULL : value);
   }

   /**
    * Removes the key (and its corresponding value) from this map. This method
    * does nothing if the key is not in the map.
    *
    * @param key the key that needs to be removed.
    *
    * @return the value to which the key had been mapped in this map, or
    *         <code>null</code> if the key did not have a mapping.
    */
   @Override
   public final synchronized V remove(Object key) {
      final Object mapKey = key == null ? NULL : key;

      if(!map.containsKey(mapKey)) {
         return null;
      }

      for(int i = 0; i < keys.size(); i++) {
         if(keys.get(i).equals(key)) {
            V val = values.get(i);
            keys.remove(i);
            values.remove(i);
            map.remove(mapKey);
            return val;
         }
      }

      return null;
   }

   /**
    * Removes the key (and its corresponding value) at an index from this map.
    *
    * @return the value to which the key had been mapped in this map, or
    *         <code>null</code> if the key did not have a mapping.
    */
   public final synchronized V remove(int index) {
      K key = keys.remove(index);
      map.remove(key == null ? NULL : key);
      return values.remove(index);
   }

   /**
    * Exchange the positions of two specified Objects.
    *
    * @param idx1 the first index.
    * @param idx2 the second index.
    */
   public void exchange(int idx1, int idx2) {
      V value1 = getValue(idx1);
      K key1 = getKey(idx1);
      V value2 = getValue(idx2);
      K key2 = getKey(idx2);

      values.set(idx1, value2);
      keys.set(idx1, key2);
      values.set(idx2, value1);
      keys.set(idx2, key1);
   }

   /**
    * Clears this map so that it contains no keys.
    */
   @Override
   public final synchronized void clear() {
      keys.clear();
      values.clear();
      map.clear();
   }

   /**
     * Returns a set view of the keys contained in this map.  The set is backed
     * by the map, so changes to the map are reflected in the set, and
     * vice-versa.  If the map is modified while an iteration over the set is
     * in progress, the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding mapping from
     * the map, via the <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt> <tt>retainAll</tt>, and <tt>clear</tt> operations.
     * It does not support the add or <tt>addAll</tt> operations.
     *
     * @return a set view of the keys contained in this map.
     */
    @Override
    public final synchronized Set<K> keySet() {
       return new LinkedHashSet<K>(keys) {
          @Override
         public Iterator<K> iterator() {
             return keys.iterator();
          }
       };
    }

    /**
     * Get the key list.
     *
     * @return the cloned key list.
     */
    @SuppressWarnings("unchecked")
    public final synchronized List<K> keyList() {
       return keys.clone();
    }

    /**
     * Returns a collection view of the values contained in this map.  The
     * collection is backed by the map, so changes to the map are reflected in
     * the collection, and vice-versa.  If the map is modified while an
     * iteration over the collection is in progress, the results of the
     * iteration are undefined.  The collection supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt> and <tt>clear</tt> operations.
     * It does not support the add or <tt>addAll</tt> operations.
     *
     * @return a collection view of the values contained in this map.
     */
    @Override
    public final synchronized Collection<V> values() {
       return values;
    }

    /**
     * Returns a set view of the mappings contained in this map.  Each element
     * in the returned set is a {@link Map.Entry}.  The set is backed by the
     * map, so changes to the map are reflected in the set, and vice-versa.
     * If the map is modified while an iteration over the set is in progress,
     * the results of the iteration are undefined.  The set supports element
     * removal, which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not support
     * the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a set view of the mappings contained in this map.
     */
    @Override
    public final synchronized Set<Map.Entry<K, V>> entrySet() {
       return new AbstractSet<Map.Entry<K, V>>() {
         @Override
         public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator();
         }

         @Override
         public int size() {
            return keys.size();
         }
       };
    }

   /**
    * Creates a map containing the same mapping.
    */
   public final synchronized Hashtable<K, V> getHashtable() {
      Hashtable<K, V> tbl = new Hashtable<>();

      for(int i = 0; i < keys.size(); i++) {
         tbl.put(keys.get(i), values.get(i));
      }

      return tbl;
   }

   /**
    * Puts all name/value pairs from the map into the current map.
    */
   @Override
   public final synchronized void putAll(Map<? extends K, ? extends V> map) {
      Iterator<? extends K> keys = map.keySet().iterator();

      while(keys.hasNext()) {
         K key = keys.next();
         V val = map.get(key);
         put(key, val);
      }
   }

   /**
    * Gets the key at the specified index.
    *
    * @param idx the index of the key.
    *
    * @return the key.
    */
   public final synchronized K getKey(int idx) {
      return keys.get(idx);
   }

   /**
    * Gets the value at the specified index.
    *
    * @param idx the index of the value.
    *
    * @return the value.
    */
   public final synchronized V getValue(int idx) {
      return values.get(idx);
   }

   /**
    * Gets the index of a key in the sorted list.
    *
    * @param key the key object.
    *
    * @return the index of the key.
    */
   public final synchronized int indexOf(K key) {
      return !map.containsKey(key == null ? NULL : key) ? -1 : keys.indexOf(key);
   }

   /**
    * Creates a shallow copy of this map. All the structure of the
    * map itself is copied, but the keys and values are not cloned.
    * This is a relatively expensive operation.
    *
    * @return a clone of the map.
    */
   @SuppressWarnings("unchecked")
   @Override
   public final synchronized Object clone() {
      try {
         OrderedMap<K, V> t = (OrderedMap<K, V>) super.clone();

         t.keys = keys.clone();
         t.values = values.clone();
         t.map = new ConcurrentHashMap<>(map);
         return t;
      }
      catch(CloneNotSupportedException e) {
      }

      return null;
   }

   /**
    * Returns the string representation.
    *
    * @return the string representation.
    */
   @Override
   public final synchronized String toString() {
      StringBuilder buf = new StringBuilder("OrderedMap[");

      for(int i = 0; i < keys.size(); i++) {
         if(i > 0) {
            buf.append(",");
         }

         buf.append(keys.get(i) + "=" + values.get(i));
      }

      buf.append("]");

      return buf.toString();
   }

   /**
    * Sorts the keys of this map in their natural ascending order. Note that
    * all the keys must implement the Comparable interface and must be mutually
    * comparable (that is, key1.compareTo(key2) must not throw a
    * ClassCastException for any keys key1 and key2 in the map).
    */
   public final synchronized void sort() {
      sort(new Comparator<Object>() {
         @Override
         @SuppressWarnings({ "unchecked", "rawtypes" })
         public int compare(Object o1, Object o2) {
            return ((Comparable) o1).compareTo(o2);
         }
      });
   }

   /**
    * Sorts the keys of this map according to the order induced by the specified
    * comparator. All keys must be mutually comparable using the specified
    * comparator (that is, c.compare(key1, key2) must not throw a
    * ClassCastException for any elements key1 and key2 in the map).
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   public final synchronized void sort(final Comparator comp) {
      Comparator<Object> c = new Comparator<Object>() {
         @Override
         public int compare(Object o1, Object o2) {
            Object[] a1 = (Object[]) o1;
            Object[] a2 = (Object[]) o2;

            return comp.compare(a1[0], a2[0]);
         }
      };

      // Collections.sort() is implemented by converting the list to an array,
      // calling Arrays.sort() and then converting back to a list, so were're
      // not incurring too much additional overhead by doing it this way.
      Object[] array = new Object[keys.size()];

      for(int i = 0; i < array.length; i++) {
         array[i] = new Object[] { keys.get(i), values.get(i) };
      }

      Arrays.sort(array, c);

      ObjectArrayList<K> keys2 = new ObjectArrayList<>();
      ObjectArrayList<V> values2 = new ObjectArrayList<>();

      for(int i = 0; i < array.length; i++) {
         Object[] a = (Object[]) array[i];
         keys2.add((K) a[0]);
         values2.add((V) a[1]);
      }

      keys = keys2;
      values = values2;
   }

   @SuppressWarnings("rawtypes")
   @Override
   public boolean equals(Object o) {
      if(o == this) {
         return true;
      }

      if(!(o instanceof OrderedMap)) {
         return false;
      }

      return Tool.equals(keys, ((OrderedMap) o).keys) &&
         Tool.equals(values, ((OrderedMap) o).values);
   }

   private static final String NULL = new String("NULL");
   private ObjectArrayList<K> keys = new ObjectArrayList<>();
   private ObjectArrayList<V> values = new ObjectArrayList<>();
   private ConcurrentHashMap<Object, Object> map = new ConcurrentHashMap<>();

   private final class Entry implements Map.Entry<K, V> {
      public Entry(int index, K key) {
         this.index = index;
         this.key = key;
      }

      @Override
      public K getKey() {
         return key;
      }

      @Override
      public V getValue() {
         Object val = map.get(key);
         return val == NULL ? null : (V) val;
      }

      @Override
      public V setValue(V value) {
         values.set(index, value);
         Object oval = map.put(key, value == null ? NULL : value);
         return oval == NULL ? null : (V) oval;
      }

      private final int index;
      private final K key;
   }

   private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
      public EntryIterator() {
         keyIterator = keys.iterator();
      }

      @Override
      public boolean hasNext() {
         return keyIterator.hasNext();
      }

      @Override
      public Map.Entry<K, V> next() {
         if(!keyIterator.hasNext()) {
            throw new NoSuchElementException();
         }

         return new Entry(++i, keyIterator.next());
      }

      @Override
      public void remove() {
         if(i < 0) {
            throw new IllegalStateException("Before start of iterator");
         }

         K key = keys.get(i);
         keyIterator.remove();
         map.remove(key == null ? NULL : key);
         values.remove(i);
         --i;
      }

      private Iterator<K> keyIterator;
      private int i = -1;
   }
}
