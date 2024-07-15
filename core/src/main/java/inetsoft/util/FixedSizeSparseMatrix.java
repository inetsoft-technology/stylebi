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

import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.Arrays;

/**
 * A fixed size memory efficient sparse matrix. It guarantees that there is no
 * object creation when getting object. For optimization, the size should be set
 * to be a prime number which is close to the actually occupied size.
 * The set values are not always kept for its size is fixed, so it's better to
 * just use the sparse matrix as a cache.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public class FixedSizeSparseMatrix implements Cloneable, Serializable {
   /**
    * A special object value marking a value that does not exist in matrix.
    */
   public static final Object NULL = SparseMatrix.NULL;

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
   public FixedSizeSparseMatrix() {
      this.size = DEFAULT_SIZE;
   }

   /**
    * Create an empty sparse matrix with a specified size.
    * @param size the hash list size. It should be a prime number.
    */
   public FixedSizeSparseMatrix(int size) {
      size = Arrays.binarySearch(VALID_SIZES, size) < 0 ? DEFAULT_SIZE : size;
      this.size = size;
   }

   /**
    * Remove all data in the matrix.
    */
   public void clear() {
      this.valReference = null;
   }

   /**
    * Set the value of a cell in the matrix.
    */
   public final void set(int row, int col, Object val) {
      SoftReference<KeyVals> valReference = this.valReference;
      KeyVals keyVals = valReference == null ? null : valReference.get();

      if(keyVals == null) {
         this.valReference = new SoftReference<>(keyVals = new KeyVals(size));
      }

      long along = (((long) row) << 32) | col;
      int hash = (int) (along % size);
      keyVals.keys[hash] = along;
      keyVals.vals[hash] = val;
   }

   /**
    * Get the value of a cell in the matrix. If the cell value has not been
    * set, return NULL.
    */
   public final Object get(int row, int col) {
      SoftReference<KeyVals> valReference = this.valReference;
      KeyVals keyVals = valReference == null ? null : valReference.get();

      if(keyVals == null) {
         return NULL;
      }

      long along = (((long) row) << 32) | col;
      int hash = (int) (along % size);
      return keyVals.keys[hash] == along ? keyVals.vals[hash] : NULL;
   }

   private static class KeyVals {
      public KeyVals(int size) {
         this.keys = new long[size];
         this.vals = new Object[size];

         for(int i = 0; i < size; i++) {
            keys[i] = -1;
         }
      }

      private long[] keys;
      private Object[] vals;
   }

   // default size
   private static final int DEFAULT_SIZE = 997;

   private final int size;
   private transient SoftReference<KeyVals> valReference;
}
