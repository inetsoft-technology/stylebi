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
package inetsoft.util.swap;

import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;

/**
 * XWeakList, the weak reference list implementation.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XWeakList implements Cloneable {
   /**
    * Create an instance of <tt>XWeakList</tt>.
    */
   public XWeakList() {
      this(10);
   }

   /**
    * Create an instance of <tt>XWeakList</tt>.
    * @param size the specified initial size.
    */
   public XWeakList(int size) {
      super();
      this.arr = new WeakReference[size];
   }

   /**
    * Trim the capacity of this <tt>XWeakList</tt> instance to be the
    * list's current size.
    */
   public void trimToSize() {
      int osize = arr.length;

      if(size < osize) {
         WeakReference[] oarr = arr;
         arr = new WeakReference[size];
         System.arraycopy(oarr, 0, arr, 0, size);
      }
   }

   /**
    * Increase the capacity of this <tt>XWeakList</tt> instance.
    * @param msize the desired minimum capacity.
    */
   public void ensureCapacity(int msize) {
      int osize = arr.length;

      if(msize > osize) {
         WeakReference[] oarr = arr;
         int nsize = (int) (osize * 1.5);

         if(nsize < msize) {
            nsize = msize;
         }

         arr = new WeakReference[nsize];
         System.arraycopy(oarr, 0, arr, 0, size);
      }
   }

   /**
    * Return the number of elements in this list.
    * @return the number of elements in this list.
    */
   public int size() {
      return size;
   }

   /**
    * Remove the specified element.
    * @param elem the specified element to remove.
    * @return <tt>true</tt> if the specified element is removed, <tt>false</tt>
    * otherwise.
    */
   public boolean remove(Object elem) {
      for(int i = 0; i < size; i++) {
         if(Tool.equals(elem, arr[i].get())) {
            remove(i);

            return true;
         }
      }

      return false;
   }

   /**
    * Check if this list has no elements.
    * @return  <tt>true</tt> if this list has no elements; <tt>false</tt>
    * otherwise.
    */
   public boolean isEmpty() {
      return size == 0;
   }

   /**
    * Check if this list contains the specified element.
    * @param elem element whose presence in this List is to be tested.
    * @return <tt>true</tt> if the specified element is present; <tt>false</tt>
    * otherwise.
    */
   public boolean contains(Object elem) {
      return indexOf(elem) >= 0;
   }

   /**
    * Search for the first occurence of the given argument.
    * @param elem an object.
    * @return the index of the first occurrence of the argument in this list;
    * <tt>-1</tt> if not found.
    */
   public int indexOf(Object elem) {
      for(int i = 0; i < size; i++) {
         if(Tool.equals(elem, arr[i].get())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Return the index of the last occurrence of the specified object in this
    * list.
    * @param elem the desired element.
    * @return the index of the last occurrence of the specified object in this
    * list; if the object is not found.
    */
   public int lastIndexOf(Object elem) {
      for(int i = size - 1; i >= 0; i--) {
         if(Tool.equals(elem, arr[i].get())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Clone this object.
    * @return a clone of this <tt>XWeakList</tt> instance.
    */
   @Override
   public Object clone() {
      try {
         XWeakList v = (XWeakList) super.clone();
         v.arr = new WeakReference[size];
         System.arraycopy(arr, 0, v.arr, 0, size);

         return v;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone XWeakList", ex);
      }

      return null;
   }

   /**
    * Return an array containing all of the elements in this list.
    * @return an array containing all of the elements in this list.
    */
   public Object[] toArray() {
      Object[] result = new Object[size];

      for(int i = 0; i < size; i++) {
         result[i] = arr[i].get();
      }

      return result;
   }

   /**
    * Return an array containing all of the elements in this list.
    * @param a the array into which the elements of the list are to be stored,
    * if it is big enough; otherwise, a new array of the same runtime type is
    * allocated for this purpose.
    * @return an array containing the elements of the list.
    */
   public Object[] toArray(Object[] a) {
      if(a.length < size) {
         Class cls = a.getClass();
         a = (Object[]) Array.newInstance(cls.getComponentType(), size);
      }

      for(int i = 0; i < size; i++) {
         a[i] = arr[i].get();
      }

      return a;
   }

   /**
    * Return the element at the specified position in this list.
    * @param index index of element to return.
    * @return the element at the specified position in this list.
    */
   public Object get(int index) {
      return arr[index] == null ? null : arr[index].get();
   }

   /**
    * Replace the element at the specified position in this list with
    * the specified element.
    * @param index index of element to replace.
    * @param element element to be stored at the specified position.
    * @return the element previously at the specified position.
    */
   public void set(int index, Object element) {
      arr[index] = new WeakReference(element);
   }

   /**
    * Append the specified element to the end of this list.
    * @param o element to be appended to this list.
    * @return <tt>true</tt>.
    */
   public boolean add(Object o) {
      ensureCapacity(size + 1);
      arr[size++] = new WeakReference(o);
      return true;
   }

   /**
    * Insert the specified element at the specified position in this list.
    * @param index index at which the specified element is to be inserted.
    * @param element element to be inserted.
    */
   public void add(int index, Object element) {
      ensureCapacity(size + 1);
      System.arraycopy(arr, index, arr, index + 1, size - index);
      arr[index] = new WeakReference(element);
      size++;
   }

   /**
    * Remove the element at the specified position in this list.
    * @param index the index of the element to removed.
    * @return the element that was removed from the list.
    */
   public void remove(int index) {
      int numMoved = size - index - 1;

      if(numMoved > 0) {
         System.arraycopy(arr, index + 1, arr, index, numMoved);
      }

      arr[--size] = null;
   }

   /**
    * Remove all of the elements from this list.
    */
   public void clear() {
      for(int i = 0; i < size; i++) {
         arr[i] = null;
      }

      size = 0;
   }

   /**
    * Append all of the elements in the specified Collection to the end of
    * this list.
    * @param c the elements to be inserted into this list.
    * @return <tt>true</tt> if this list changed as a result of the call.
    */
   public boolean addAll(Collection c) {
      int numNew = c.size();
      ensureCapacity(size + numNew);
      Iterator e = c.iterator();

      for(int i = 0; i < numNew; i++) {
         arr[size++] = new WeakReference(e.next());
      }

      return numNew != 0;
   }

   /**
    * Insert all of the elements in the specified Collection into this
    * list, starting at the specified position.
    * @param index index at which to insert first element.
    * @param c elements to be inserted into this list.
    * @return <tt>true</tt> if this list changed as a result of the call.
    */
   public boolean addAll(int index, Collection c) {
      int numNew = c.size();
      ensureCapacity(size + numNew);
      int numMoved = size - index;

      if(numMoved > 0) {
         System.arraycopy(arr, index, arr, index + numNew, numMoved);
      }

      Iterator e = c.iterator();

      for(int i = 0; i < numNew; i++) {
         arr[index++] = new WeakReference(e.next());
      }

      size += numNew;
      return numNew != 0;
   }

   /**
    * Remove from this List all of the elements whose index is between
    * fromIndex, inclusive and toIndex, exclusive.
    * @param fromIndex index of first element to be removed.
    * @param toIndex index after last element to be removed.
    */
   protected void removeRange(int fromIndex, int toIndex) {
      int numMoved = size - toIndex;
      System.arraycopy(arr, toIndex, arr, fromIndex, numMoved);
      int newSize = size - (toIndex - fromIndex);

      while(size != newSize) {
         arr[--size] = null;
      }
   }

   /**
    * Shrink the weak list.
    */
   public void shrink() {
      for(int i = size - 1; i >= 0; i--) {
         if(arr[i].get() == null) {
            remove(i);
         }
      }
   }

   protected WeakReference[] arr;
   protected int size;
   protected int capacity;

   private static final Logger LOG =
      LoggerFactory.getLogger(XWeakList.class);
}
