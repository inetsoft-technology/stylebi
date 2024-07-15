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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Implementation of an ordered set. This is like a set but the items
 * are kept in the same order as they are added. It is NOT efficient and
 * should only be used for small data collection.
 */
public final class OrderedSet<E> implements Set<E>, Cloneable, Serializable {
   public OrderedSet() {
      set.defaultReturnValue(-1);
   }

   public E get(int idx) {
      return vec.get(idx);
   }

   public E firstElement() {
      return vec.firstElement();
   }

   @Override
   public int size() {
      return set.size();
   }

   @Override
   public synchronized void clear() {
      set.clear();
      vec.removeAllElements();
   }

   @Override
   public boolean isEmpty() {
      return set.isEmpty();
   }

   @Override
   public Object[] toArray() {
      return vec.toArray();
   }

   @Override
   public synchronized boolean add(E val) {
      int idx = set.putIfAbsent(val, set.size());

      if(idx < 0) {
         vec.add(val);
      }

      return idx < 0;
   }

   @Override
   public boolean contains(Object val) {
      return set.containsKey(val);
   }

   @SuppressWarnings("unchecked")
   @Override
   public boolean equals(Object obj) {
      try {
         OrderedSet setObj = (OrderedSet) obj;

         return vec.equals(setObj.vec);
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   public synchronized boolean remove(Object val) {
      Integer rc = set.remove(val);

      if(rc != null) {
         vec.removeElement(val);
      }

      return rc != null;
   }

   @Override
   public synchronized boolean addAll(Collection<? extends E> coll) {
      Iterator<? extends E> iter = coll.iterator();
      boolean rc = false;

      while(iter.hasNext()) {
         rc = add(iter.next()) || rc;
      }

      return rc;
   }

   @Override
   public boolean containsAll(Collection<?> coll) {
      return set.keySet().containsAll(coll);
   }

   @Override
   public synchronized boolean removeAll(Collection<?> coll) {
      Iterator<?> iter = coll.iterator();
      boolean rc = false;

      while(iter.hasNext()) {
         rc = remove(iter.next()) || rc;
      }

      return rc;
   }

   @Override
   public synchronized boolean retainAll(Collection<?> coll) {
      boolean rc = set.keySet().retainAll(coll);

      for(int i = 0; i < vec.size(); i++) {
         if(!set.containsKey(vec.get(i))) {
            vec.removeElementAt(i);
            i--;
         }
      }

      return rc;
   }

   @Override
   public Iterator<E> iterator() {
      return vec.iterator();
   }

   @Override
   public <T> T[] toArray(T[] arr) {
      return vec.toArray(arr);
   }

   public synchronized void reverse() {
      Collections.reverse(vec);
   }

   @Override
   public String toString() {
      return vec.toString();
   }

   public int indexOf(E val) {
      return set.getInt(val);
   }

   @SuppressWarnings("unchecked")
   @Override
   public Object clone() {
      try {
         OrderedSet<E> oset = (OrderedSet<E>) super.clone();
         oset.set = set.clone();
         oset.vec = (Vector<E>) vec.clone();
         return oset;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone OrderedSet", ex);
         return null;
      }
   }

   private Object2IntOpenHashMap<E> set = new Object2IntOpenHashMap<>();
   private Vector<E> vec = new Vector<>();

   private static final Logger LOG = LoggerFactory.getLogger(OrderedSet.class);
}
