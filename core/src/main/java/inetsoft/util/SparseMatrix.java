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

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.locks.StampedLock;

/**
 * A memory efficient sparse matrix. It guarantees that there is no object
 * creation when getting object. For optimization, the size should be set
 * to be a prime number which is close to the actually occupied size.
 * <p>
 * The matrix is safe for concurrent use by any number of readers and writers
 * (get, set, clear, toString and serialization). A reader never sees another
 * cell's value. A set that races a clear() may be lost, like any other entry
 * that clear() removes.
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
      ObjectList[] arr = olist;

      // install the bucket array and the bucket with a CAS, so that two first
      // writers never drop each other's entries
      if(arr == null) {
         ObjectList[] narr = new ObjectList[size];
         arr = (ObjectList[]) OLIST.compareAndExchange(this, (ObjectList[]) null, narr);
         arr = arr == null ? narr : arr;
      }

      ObjectList list = (ObjectList) BUCKET.getAcquire(arr, hash);

      if(list == null) {
         ObjectList nlist = new ObjectList();
         list = (ObjectList) BUCKET.compareAndExchange(arr, hash, (ObjectList) null, nlist);
         list = list == null ? nlist : list;
      }

      list.set(along, val);
   }

   /**
    * Get the value of a cell in the matrix. If the cell value has not been
    * set, return NULL.
    */
   public Object get(int row, int col) {
      ObjectList[] arr = olist;

      if(arr == null) {
         return NULL;
      }

      long along = getLong(row, col);
      int hash = (int) (along % size);
      ObjectList list = (ObjectList) BUCKET.getAcquire(arr, hash);
      return list == null ? NULL : list.get(along);
   }

   /**
    * To string.
    */
   public String toString() {
      StringBuilder sb = new StringBuilder();
      ObjectList[] arr = olist;

      for(int i = 0; arr != null && i < arr.length; i++) {
         if(i > 0) {
            sb.append("\n");
         }

         sb.append((ObjectList) BUCKET.getAcquire(arr, i));
      }

      return sb.toString();
   }

   /**
    * Write the default serialized form (fields size and olist) from a snapshot
    * of the bucket array read with acquire semantics, so a bucket installed by a
    * concurrent set is either left out or written fully constructed.
    */
   private void writeObject(ObjectOutputStream out) throws IOException {
      ObjectList[] arr = olist;
      ObjectList[] snapshot = null;

      if(arr != null) {
         snapshot = new ObjectList[arr.length];

         for(int i = 0; i < arr.length; i++) {
            snapshot[i] = (ObjectList) BUCKET.getAcquire(arr, i);
         }
      }

      ObjectOutputStream.PutField fields = out.putFields();
      fields.put("size", size);
      fields.put("olist", snapshot);
      out.writeFields();
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
    * Inner object list class. Keys and values are kept in two parallel sorted
    * lists that an insert shifts, so a read must see both from one point in
    * time: writes take the write lock, and reads use a validated optimistic
    * read that falls back to the read lock.
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
       * Create an object list holding deserialized state.
       */
      private ObjectList(long[] llist, List olist, int usedpos) {
         this.llist = llist;
         this.olist = olist;
         this.usedpos = usedpos;
      }

      /**
       * Set an object.
       */
      public void set(long along, Object obj) {
         long stamp = lock.writeLock();

         try {
            int pos = binarySearch(llist, usedpos, along);

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
         finally {
            lock.unlockWrite(stamp);
         }
      }

      /**
       * Get an object.
       */
      public Object get(long along) {
         long stamp = lock.tryOptimisticRead();

         if(stamp != 0L) {
            Object val = null;
            boolean failed = false;

            // while a writer runs, llist, usedpos and olist may be torn. The
            // search is bounded by the array length, and a result or exception
            // is only trusted if validate() shows no write overlapped it.
            try {
               int pos = binarySearch(llist, usedpos, along);
               val = pos < 0 ? NULL : olist.get(pos);
            }
            catch(RuntimeException ex) {
               failed = true;
            }

            if(!failed && lock.validate(stamp)) {
               return val;
            }
         }

         stamp = lock.readLock();

         try {
            int pos = binarySearch(llist, usedpos, along);
            return pos < 0 ? NULL : olist.get(pos);
         }
         finally {
            lock.unlockRead(stamp);
         }
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
         long stamp = lock.readLock();

         try {
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
         finally {
            lock.unlockRead(stamp);
         }
      }

      /**
       * Write a consistent state; concurrent sets wait for the read lock.
       */
      private void writeObject(ObjectOutputStream out) throws IOException {
         long stamp = lock.readLock();

         try {
            out.defaultWriteObject();
         }
         finally {
            lock.unlockRead(stamp);
         }
      }

      /**
       * Replace a deserialized list, whose transient lock is not initialized,
       * with one that has a lock.
       */
      private Object readResolve() {
         return new ObjectList(llist, olist, usedpos);
      }

      /**
       * Binary search. The high bound is capped by the array length so that a
       * torn optimistic read of usedpos and llist cannot index out of bounds.
       */
      private static int binarySearch(long[] llist, int usedpos, long along) {
         int low = 0;
         int high = Math.min(usedpos, llist.length - 1);

         while(low <= high) {
            int mid = (low + high) >>> 1;
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

      // the computed value before #77431, pinned so serialized lens chains
      // written by earlier builds (e.g. DistributedTableCacheStore) stay readable
      private static final long serialVersionUID = 6431452410715692690L;
      private static final int DEFAULT_INITIAL_SIZE = 1;
      private final transient StampedLock lock = new StampedLock();
      private int usedpos = -1;
      private long[] llist;
      private List olist;
   }

   // the computed value before #77431, pinned so serialized lens chains
   // written by earlier builds (e.g. DistributedTableCacheStore) stay readable
   private static final long serialVersionUID = -598829746600398701L;
   // default size
   private static final int DEFAULT_SIZE = 997;
   private static final VarHandle OLIST;
   private static final VarHandle BUCKET =
      MethodHandles.arrayElementVarHandle(ObjectList[].class);

   static {
      try {
         OLIST = MethodHandles.lookup()
            .findVarHandle(SparseMatrix.class, "olist", ObjectList[].class);
      }
      catch(ReflectiveOperationException ex) {
         throw new ExceptionInInitializerError(ex);
      }
   }

   private final int size;
   private volatile ObjectList[] olist;
}
