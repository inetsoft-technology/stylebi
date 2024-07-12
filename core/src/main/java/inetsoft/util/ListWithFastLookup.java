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

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * List implementation augmented to support fast lookup methods contains and indexOf.
 */
public class ListWithFastLookup<E> extends ArrayList<E> {
   public ListWithFastLookup() {
      super();
   }

   public ListWithFastLookup(Collection<? extends E> c) {
      super(c);
   }

   public ListWithFastLookup(int initialCapacity) {
      super(initialCapacity);
   }

   @Override
   public boolean add(E e) {
      lock.lock();

      try {
         if(indexOfs != null && !indexOfs.containsKey(e)) {
            indexOfs.put(e, size());
         }
      }
      finally {
         lock.unlock();
      }

      return super.add(e);
   }

   @Override
   public E set(int index, E element) {
      final E oldElement = super.set(index, element);
      modified();
      return oldElement;
   }

   @Override
   public void add(int index, E element) {
      super.add(index, element);
      modified();
   }

   @Override
   public E remove(int index) {
      final E removedElement = super.remove(index);
      modified();
      return removedElement;
   }

   @Override
   public int indexOf(Object o) {
      Object2IntMap<E> localIndexOfs = getIndexMap();

      final Integer index = localIndexOfs.get(o);
      return index == null ? -1 : index;
   }

   private Object2IntMap<E> getIndexMap() {
      Object2IntMap<E> localIndexOfs;

      if((localIndexOfs = indexOfs) == null) {
         lock.lock();

         try {
            if((localIndexOfs = indexOfs) == null) {
               generateIndexOfs();
               localIndexOfs = indexOfs;
            }
         }
         finally {
            lock.unlock();
         }
      }

      return localIndexOfs;
   }

   @Override
   public void clear() {
      super.clear();
      this.modified();
   }

   @Override
   public boolean addAll(int index, Collection<? extends E> c) {
      final boolean changed = super.addAll(index, c);

      if(changed) {
         modified();
      }

      return changed;
   }

   @Override
   public boolean equals(Object o) {
      return super.equals(o);
   }

   @Override
   public int hashCode() {
      return super.hashCode();
   }

   @Override
   protected void removeRange(int fromIndex, int toIndex) {
      modified();
      super.removeRange(fromIndex, toIndex);
   }

   @Override
   public boolean contains(Object o) {
      return getIndexMap().containsKey(o);
   }

   @Override
   public boolean remove(Object o) {
      if(!contains(o)) {
         return false;
      }

      final boolean removed = super.remove(o);

      if(removed) {
         modified();
      }

      return removed;
   }

   @Override
   public boolean addAll(Collection<? extends E> c) {
      final boolean changed = super.addAll(c);

      if(changed) {
         modified();
      }

      return changed;
   }

   @Override
   public boolean removeAll(Collection<?> c) {
      final boolean changed = super.removeAll(c);

      if(changed) {
         modified();
      }

      return changed;
   }

   @Override
   public boolean retainAll(Collection<?> c) {
      final boolean changed = super.retainAll(c);

      if(changed) {
         modified();
      }

      return changed;
   }

   @Override
   public void sort(Comparator<? super E> c) {
      modified();
      super.sort(c);
   }

   private void modified() {
      lock.lock();

      try {
         indexOfs = null; // clear indexOfs on modification, create the map on-demand.
      }
      finally {
         lock.unlock();
      }
   }

   private void generateIndexOfs() {
      Object2IntMap<E> indexOfs = new Object2IntOpenHashMap<>(size());

      for(int i = 0; i < this.size(); i++) {
         final E e = get(i);

         if(!indexOfs.containsKey(e)) {
            indexOfs.put(e, i);
         }
      }

      this.indexOfs = indexOfs;
   }

   // Keep track of element counts for fast contains.
   private final ReentrantLock lock = new ReentrantLock();
   private Object2IntMap<E> indexOfs;
}
