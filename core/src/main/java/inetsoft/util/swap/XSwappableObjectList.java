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
package inetsoft.util.swap;

import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * XSwappableObjectList provides the ability to cache list data in file system,
 * which has a mechanism to swap list data between file system and memory. It is
 * most likely used for large-scale data.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public final class XSwappableObjectList<T> implements Serializable {
   /**
    * @param kryoClass if Kryo should be used for serialization, pass the
    * class of the objects on the list.
    */
   public XSwappableObjectList(Class kryoClass) {
      super();

      this.kryoClass = kryoClass;
      this.fragments = new XObjectFragment[10];
      this.pos = 0;

      if(kryoClass != null) {
         XSwapUtil.getKryo().register(kryoClass);
      }
   }

   /**
    * Return the number of rows in the list. The number of rows includes
    * the header rows.
    * @return number of rows in list.
    */
   public int size() {
      return count;
   }

   /**
    * Cut size to new size.
    */
   public void size(int size) {
      if(size < 0 || size >= count || disposed) {
         return;
      }

      XObjectFragment[] fragments = this.fragments;

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
               XObjectFragment fragment = fragments[i];
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
    * @return the value at the index.
    */
   public T get(int idx) {
      int tidx = idx >> BLOCK_BITS;
      int ridx = idx & BLOCK_SIZE;
      XObjectFragment<T>[] fragments = this.fragments;

      if(fragments == null) {
         try {
            rlock.lock();
            fragments = this.fragments;
         }
         finally {
            rlock.unlock();
         }
      }

      if(fragments == null || fragments[tidx] == null) {
         String msg = "Invalid swappable list: " +
               fragments + ", " + (fragments == null ? null : fragments[tidx]) +
               " in " + this;

         if(debug) {
            Exception ex = new Exception(msg);
            LOG.error(msg, ex);
         }
         else {
            LOG.error(msg);
         }

         return null;
      }

      // wait for memory outside (before) synchronized to avoid deadlock
      if((waitCnt++ & 0x1f) == 0) {
         XSwapper.getSwapper().waitForMemory();
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
   public Object[] getFragment(int fidx) {
      XObjectFragment<T>[] fragments = this.fragments;

      if(fragments == null) {
         try {
            rlock.lock();
            fragments = this.fragments;
         }
         finally {
            rlock.unlock();
         }
      }

      if(fragments == null || fragments[fidx] == null) {
         if(debug) {
            Exception ex = new Exception("Invalid swappable fragment: " +
               fragments + ", " + (fragments == null ? null : fragments[fidx]) +
               " in " + this);
            LOG.error(ex.getMessage(), ex);
         }

         return null;
      }

      // wait for memory outside (before) synchronized to avoid deadlock
      if((waitCnt++ & 0x1f) == 0) {
         XSwapper.getSwapper().waitForMemory();
      }

      return fragments[fidx].getArray();
   }

   /**
    * Set the object at the specified row index.
    */
   public void set(int idx, Object obj) {
      int tidx = idx >> BLOCK_BITS;
      int ridx = idx & BLOCK_SIZE;
      XObjectFragment[] fragments = this.fragments;

      synchronized(fragments[tidx]) {
         fragments[tidx].access();
         fragments[tidx].change();
         fragments[tidx].set(ridx, obj);
      }
   }

   /**
    * Add a new object to the list.
    */
   public int add(Object obj) {
      if((count & BLOCK_SIZE) == 0) {
         XSwapper.getSwapper().waitForMemory();

         if(fragment != null) {
            fragment.complete();
            fragment = null;
         }

         try {
            rlock.lock();
            fragment = new XObjectFragment((char) 128, (char) (BLOCK_SIZE + 1),
                                           kryoClass);
            ensureCapacity(fragment);
         }
         finally {
            rlock.unlock();
         }
      }

      synchronized(fragment) {
         fragment.add(obj);
      }

      count++;
      return count - 1;
   }

   public void addAll(Collection<T> values) {
      for(T val : values) {
         add(val);
      }
   }

   /**
    * Complete this list. The method MUST be called after all rows added.
    */
   public void complete() {
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
    * Check if the list has been completed.
    * @return <tt>true</tt> if completed, <tt>false</tt> otherwise.
    */
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Dispose the swappable list.
    */
   public void dispose() {
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

            if(debug) {
               Exception ex = new Exception("Dispose swappable list: " + this);
               LOG.warn(ex.getMessage(), ex);
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
   public boolean isDisposed() {
      return disposed;
   }

   /**
    * Finalize the object.
    */
   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Ensure capacity.
    */
   private void ensureCapacity(XObjectFragment nfragment) {
      if(debug && fragments == null) {
         Exception ex = new Exception("Invalid swappable list: " + fragments +
            " in " + this);
         LOG.error(ex.getMessage(), ex);
      }

      if(pos == fragments.length) {
         int nsize = (int) (fragments.length * 1.5);
         XObjectFragment[] ofragments = fragments;
         XObjectFragment[] nfragments = new XObjectFragment[nsize];
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
   public boolean isValid() {
      return true;
   }

   public Iterator<T> iterator() {
      return new MyIterator();
   }

   public Stream<T> stream() {
      return StreamSupport.stream(
         Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED), false);
   }

   private class MyIterator implements Iterator<T> {
      public MyIterator() {
         cnt = size();
      }

      @Override
      public boolean hasNext() {
         return idx < cnt;
      }

      @Override
      public T next() {
         idx++;

         if(fragment == null) {
            fragment = getFragment(fidx);
         }

         if(idx2 < fragment.length) {
            return (T) fragment[idx2++];
         }

         fragment = getFragment(++fidx);
         idx2 = 0;
         return (T) fragment[idx2++];
      }

      private int idx = 0;
      private int idx2 = 0; // index within fragment
      private int fidx = 0;
      private int cnt;
      private Object[] fragment;
   }

   private static final int BLOCK_BITS = 13;
   private static final int BLOCK_SIZE = 0x1fff;

   private final Lock rlock = new ReentrantLock(); // list row lock

   private XObjectFragment<T>[] fragments; // fragments
   private char pos; // next position
   private boolean completed = false; // data fully loaded
   private boolean disposed = false; // list disposed
   private int count; // data count
   private Class kryoClass;
   private transient short waitCnt = 0;
   private transient XObjectFragment fragment; // current list fragment
   private transient boolean debug =
      "true".equals(SreeEnv.getProperty("filter.debug", "false"));
   private static final Logger LOG =
      LoggerFactory.getLogger(XSwappableObjectList.class);
}
