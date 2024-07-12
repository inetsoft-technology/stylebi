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
package inetsoft.util.swap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * XSwappableIntList provides the ability to cache int list data in file system,
 * which has a mechanism to swap int list data between file system and memory.
 * It is most likely used for large-scale data.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XSwappableIntList implements Serializable {
   /**
    * Constuctor.
    */
   public XSwappableIntList() {
      super();

      this.fragments = new XIntFragment[10];
      this.pos = 0;
   }

   /**
    * Constructor.
    */
   public XSwappableIntList(int[] arr) {
      this(arr, arr.length);
   }

   /**
    * Constructor.
    */
   public XSwappableIntList(XIntList list) {
      this(list.getArray(), list.size());
   }

   /**
    * Constructor.
    */
   public XSwappableIntList(int[] arr, int length) {
      this();

      for(int i = 0; i < length; i++) {
         add(arr[i]);
      }
   }

   /**
    * Make a copy of the list.
    */
   public XSwappableIntList(XSwappableIntList list) {
      int n = list.size();

      for(int i = 0; i < n; i++) {
         add(list.get(i));
      }
   }

   /**
    * Return the size of this list.
    * @return the size of this list.
    */
   public final int size() {
      return count;
   }

   /**
    * Cut size to new size.
    */
   public final void size(int size) {
      if(size < 0 || size >= count || disposed) {
         return;
      }

      XIntFragment[] fragments = this.fragments;

      if(fragments == null) {
         try {
            rlock.lock();
            fragments = this.fragments;
         }
         finally {
            rlock.unlock();
         }
      }

      if(size == 0) {
         for(int i = 0; i < pos; i++) {
            if(fragments[i] != null) {
               fragments[i].dispose();
               fragments[i] = null;
            }
         }

         fragment = null;
         pos = 0;
      }
      else {
         int tidx = (size - 1) >> BLOCK_BITS;
         int ridx = (size - 1) & BLOCK_SIZE;

         try {
            rlock.lock();
            for(int i = tidx + 1; i < pos; i++) {
               XIntFragment fragment = fragments[i];
               fragment.dispose();
               fragments[i] = null;
            }

            fragment = fragments[tidx];
            fragment.size((char) (ridx + 1));
            pos = (char) (tidx + 1);
         }
         finally {
            rlock.unlock();
         }
      }

      count = size;
   }

   /**
    * Return the value at the specified index.
    * @param idx the specified index.
    * @return the int value at the index.
    */
   public final int get(int idx) {
      int tidx = idx >> BLOCK_BITS;
      int ridx = idx & BLOCK_SIZE;
      XIntFragment[] fragments = this.fragments;

      if(fragments == null) {
         try {
            rlock.lock();
            fragments = this.fragments;
         }
         finally {
            rlock.unlock();
         }
      }

      // disposed?
      if(fragments == null) {
         return -1;
      }

      return fragments[tidx].getSafely(ridx);
   }

   /**
    * Get the fragment index for index.
    */
   public static final int getFragmentIndex(int idx) {
      return idx >> BLOCK_BITS;
   }

   /**
    * Get the index within the fragment.
    */
   public static final int getSubIndex(int idx) {
      return idx & BLOCK_SIZE;
   }

   /**
    * Get the raw array used for holding the fragment values.
    */
   public final int[] getFragment(int tidx) {
      XIntFragment[] fragments = this.fragments;

      if(fragments == null) {
         try {
            rlock.lock();
            fragments = this.fragments;
         }
         finally {
            rlock.unlock();
         }
      }

      // disposed?
      if(fragments == null) {
         return null;
      }

      return fragments[tidx].getArray();
   }

   /**
    * Add a new object to the list.
    * @param val the specified int value.
    */
   public final int add(int val) {
      if((count & BLOCK_SIZE) == 0) {
         XSwapper.getSwapper().waitForMemory();

         if(fragment != null) {
            fragment.complete();
            fragment = null;
         }

         try {
            rlock.lock();
            fragment = new XIntFragment((char) 128, (char) (BLOCK_SIZE + 1));
            ensureCapacity(fragment);
         }
         finally {
            rlock.unlock();
         }
       }

      synchronized(fragment) {
         fragment.add(val);
      }

      count++;
      return count - 1;
   }

   /**
    * Complete this list. The method MUST be called after all rows added.
    */
   public final void complete() {
      if(disposed) {
         return;
      }

      try {
         rlock.lock();

         if(completed) {
            return;
         }

         if(fragment != null) {
            fragment.complete();
         }

         completed = true;
      }
      finally {
         rlock.unlock();
      }
   }

   /**
    * Check if the int list has been completed.
    * @return <tt>true</tt> if completed, <tt>false</tt> otherwise.
    */
   public final boolean isCompleted() {
      return completed;
   }

   /**
    * Dispose the swappable list.
    */
   public final void dispose() {
      try {
         rlock.lock();

         if(disposed) {
            return;
         }

         disposed = true;

         if(fragments != null) {
            for(int i = 0; i < pos; i++) {
               fragments[i].dispose();
               fragments[i] = null;
            }

            fragments = null;
         }
      }
      finally {
         rlock.unlock();
      }
   }

   /**
    * Check if the list has being disposed.
    * @return <tt>true</tt> if disposed, <tt>false</tt> otherwise.
    */
   public final boolean isDisposed() {
      return disposed;
   }

   /**
    * Finalize the object.
    */
   @Override
   protected final void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Ensure capacity.
    */
   private void ensureCapacity(XIntFragment nfragment) {
      if(pos == fragments.length) {
         int nsize = (int) (fragments.length * 1.5);
         XIntFragment[] ofragments = fragments;
         XIntFragment[] nfragments = new XIntFragment[nsize];
         System.arraycopy(ofragments, 0, nfragments, 0, ofragments.length);
         nfragments[pos++] = nfragment;
         fragments = nfragments;
      }
      else {
         fragments[pos++] = nfragment;
      }
   }

   /**
    * Check if is valid.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   public final boolean isValid() {
      return true;
   }

   private static final int BLOCK_BITS = 15;
   private static final int BLOCK_SIZE = 0x7fff;

   private final Lock rlock = new ReentrantLock(); // list row lock

   private XIntFragment[] fragments; // fragments
   private char pos; // next position
   private boolean completed = false; // data fully loaded
   private boolean disposed = false; // list disposed
   private int count; // data count
   private transient XIntFragment fragment; // current list fragment
   private static final Logger LOG =
      LoggerFactory.getLogger(XSwappableIntList.class);
}
