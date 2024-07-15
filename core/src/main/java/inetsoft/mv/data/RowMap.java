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
package inetsoft.mv.data;

import java.util.ArrayList;
import java.util.List;

/**
 * RowMap, the hash map stores row.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class RowMap {
   /**
    * Create a row map with the initial capacity.
    */
   public RowMap(int capacity) {
      capacity = roundUpToPowerOf2(capacity);
      table = new Entry[capacity];
      threshold = (int) (capacity * loadFactor);
      imask = table.length - 1;
   }

   private static int roundUpToPowerOf2(int number) {
      return number >= MAX_CAPACITY ? MAX_CAPACITY
         : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;
   }

   /**
    * Add a row to the map and return the row or an existing row with the same
    * groups.
    */
   public Entry put(MVRow row) {
      int hash = row.hashCode();
      hash ^= (hash >>> 20) ^ (hash >>> 12);
      hash = hash ^ (hash >>> 7) ^ (hash >>> 4);

      // don't ignore the top bits for small table, or with some distribution
      // all lower bits may be 0
      int idx = hash & imask;

      for(Entry node = table[idx]; node != null; node = node.next) {
         if(node.hash == hash && node.row.equals(row)) {
            return node;
         }
      }

      count++;

      if(count > threshold && table.length < MAX_CAPACITY) {
         resize(table.length * 2);
      }

      return table[idx] = new Entry(row, hash, table[idx]);
   }

   /**
    * Get all rows.
    */
   public List<MVRow> getRows() {
      List<MVRow> list = new ArrayList();

      for(Entry node : table) {
         while(node != null) {
            list.add(node.row);
            node = node.next;
         }
      }

      return list;
   }

   /**
    * Resize the hashtable.
    */
   private void resize(int size) {
      size = roundUpToPowerOf2(size);
      Entry[] ntable = new Entry[size];
      threshold = (int) (size * loadFactor);
      imask = size - 1;

      for(Entry e : table) {
         if(e != null) {
            do {
               Entry next = e.next;
               int i = e.hash & imask;
               e.next = ntable[i];
               ntable[i] = e;
               e = next;
            }
            while(e != null);
         }
      }

      table = ntable;
   }

   /**
    * Print the distribution of the entries (for debugging).
    */
   public void printStat() {
      double total = 0;
      int cnt = 0;
      int max = 0;
      int empty = 0;
      
      for(int i = 0; i < table.length; i++) {
         int n = countEntries(table[i], 0);

         if(n > 0) {
            total += n;
            cnt++;
            max = Math.max(max, n);
         }
         else {
            empty++;
         }
      }

      System.err.println("RowMap Average: " + (total / cnt) + " total: " +
                         count + " max: " + max + " empty: " + 
                         (int) (empty * 100.0 / table.length) + "%");
   }

   /**
    * Clear the RowMap.
    */
   public void clear() {
      for(int i = 0; i < table.length; i++) {
         table[i] = null;
      }
   }

   /**
    * Get the number of entries in map.
    */
   public int size() {
      return count;
   }

   /**
    * Count the number of entries in the chain.
    */
   private int countEntries(Entry root, int cnt) {
      return (root == null) ? cnt : countEntries(root.next, cnt + 1);
   }

   /**
    * Hashtable entry.
    */
   public static final class Entry {
      public Entry(MVRow row, int hash, Entry next) {
         this.row = row;
         this.hash = hash;
         this.next = next;
      }

      public MVRow row;
      int hash;
      Entry next;
   }

   private static final int MAX_CAPACITY = 1 << 30;
   private static final double loadFactor = 0.7;

   private Entry[] table;
   private int count = 0;
   private int threshold;
   private int imask;
}
