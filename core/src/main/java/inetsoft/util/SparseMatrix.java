/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import java.io.Serializable;
import java.util.*;

/**
 * A memory efficient sparse matrix. It guarantees that there is no object
 * creation when getting object. For optimization, the size should be set
 * to be a prime number which is close to the actually occupied size.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class SparseMatrix implements Cloneable, Serializable {
   /**
    * A special object value marking a value that does not exist in matrix.
    */
   public static final Object NULL = new String("undefined");

   /**
    * Valid sizes for reference.
    */
   public static final int[] VALID_SIZES = new int[] {
      3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71,
      73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151,
      157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233,
      239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317,
      331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419,
      421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503,
      509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607,
      613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701,
      709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811,
      821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911,
      919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997
   };

   /**
    * Create an empty sparse matrix.
    */
   public SparseMatrix() {
      this.size = DEFAULT_SIZE;
   }

   /**
    * Create an empty sparse matrix with a specified size.
    * @param size the hash list size, must be a prime number in arrange[3..999]
    */
   public SparseMatrix(int size) {
      size = Arrays.binarySearch(VALID_SIZES, size) < 0 ?
         DEFAULT_SIZE : size;
      this.size = size;
   }

   /**
    * Remove all data in the matrix.
    */
   public void clear() {
      olist = null;
   }

   /**
    * Set the value of a cell in the matrix.
    */
   public void set(int row, int col, Object val) {
      long along = getLong(row, col);
      int hash = (int) (along % size);

      if(olist == null) {
         olist = new ObjectList[size];
      }

      if(olist[hash] == null) {
         olist[hash] = new ObjectList();
      }

      olist[hash].set(along, val);
   }

   /**
    * Get the value of a cell in the matrix. If the cell value has not been
    * set, return NULL.
    */
   public Object get(int row, int col) {
      if(olist == null) {
         return NULL;
      }

      long along = getLong(row, col);
      int hash = (int) (along % size);
      return olist[hash] == null ? NULL : olist[hash].get(along);
   }

   /**
    * To string.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; olist != null && i < size; i++) {
         if(i > 0) {
            sb.append("\n");
         }

         sb.append(olist[i]);
      }

      return sb.toString();
   }

   /**
    * Get a long value from a row and col.
    */
   private long getLong(int row, int col) {
      if(row < 0 || col < 0) {
         throw new RuntimeException("Invalid row/col found: " + row +
            ", " + col);
      }

      return (((long) row) << 32) | col;
   }

   /**
    * Inner object list class.
    */
   private static class ObjectList implements Cloneable, Serializable {
      /**
       * Create an empty object list.
       */
      public ObjectList() {
         this(DEFAULT_INITIAL_SIZE);
      }

      /**
       * Create an empty object list.
       */
      public ObjectList(int isize) {
         isize  = isize <= 0 ? DEFAULT_INITIAL_SIZE : isize;
         llist = new long[isize];
         olist = new ArrayList(isize);
      }

      /**
       * Set an object.
       */
      public void set(long along, Object obj) {
         int pos = binarySearch(along);

         if(pos >= 0) {
            olist.set(pos, obj);
         }
         else {
            ensureCapacity();
            pos = -(pos + 1);

            for(int i = usedpos; i >= pos; i--) {
               llist[i + 1] = llist[i];
            }

            llist[pos] = along;
            olist.add(pos, obj);
            usedpos++;
         }
      }

      /**
       * Get an object.
       */
      public Object get(long along) {
         int pos = binarySearch(along);
         return pos < 0 ? NULL : olist.get(pos);
      }

      /**
       * Ensure capacity.
       */
      private void ensureCapacity() {
         if(usedpos == llist.length - 1) {
            long[] nllist = new long[getNewSize()];
            System.arraycopy(llist, 0, nllist, 0, llist.length);
            llist = nllist;
         }
      }

      /**
       * Get new Size.
       */
      private int getNewSize() {
         return (llist.length < 3) ?
            (llist.length * 2) : (int) (llist.length * 1.5);
      }

      /**
       * To string.
       */
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("[");

         for(int i = 0; i <= usedpos; i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(llist[i]);
         }

         sb.append("#");
         sb.append(olist);
         sb.append("]");

         return sb.toString();
      }

      /**
       * Binary search.
       */
      private int binarySearch(long along) {
         int low = 0;
         int high = usedpos;

         while(low <= high) {
            int mid = (low + high) / 2;
            long midlong = llist[mid];

            if(midlong < along) {
               low = mid + 1;
            }
            else if(midlong > along) {
               high = mid - 1;
            }
            else {
               return mid;
            }
         }

         return -(low + 1);
      }

      private static final int DEFAULT_INITIAL_SIZE = 1;
      private int usedpos = -1;
      private long[] llist;
      private List olist;
   }

   // default size
   private static final int DEFAULT_SIZE = 997;

   private int size;
   private ObjectList[] olist;
}
