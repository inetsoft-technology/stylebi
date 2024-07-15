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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.*;

/**
 * XObjectList, the inner size and array is accessable.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XObjectList extends AbstractList implements List, Cloneable,
   Serializable
{
   /**
    * Create an instance of <tt>XObjectList</tt>.
    */
   public XObjectList() {
      this(10);
   }

   /**
    * Create an instance of <tt>XObjectList</tt>.
    * @param size the specified initial size.
    */
   public XObjectList(int size) {
      super();
      this.arr = new Object[size];
   }

   /**
    * Create an instance of <tt>XObjectList</tt>.
    * @param c the specified collection copy data from.
    */
   public XObjectList(Collection c) {
      size = c.size();
      arr = new Object[(int) Math.min((size * 110L) / 100, Integer.MAX_VALUE)];
      c.toArray(arr);
  }

   /**
    * Trim the capacity of this <tt>XObjectList</tt> instance to be the
    * list's current size.
    */
   public void trimToSize() {
      modCount++;
      int osize = arr.length;

      if(size < osize) {
         Object[] oarr = arr;
         arr = new Object[size];
         System.arraycopy(oarr, 0, arr, 0, size);
      }
   }

   /**
    * Increase the capacity of this <tt>XObjectList</tt> instance.
    * @param msize the desired minimum capacity.
    */
   public void ensureCapacity(int msize) {
      modCount++;
      int osize = arr.length;

      if(msize > osize) {
         Object[] oarr = arr;
         int nsize = (int) (osize * 1.5);

         if(nsize < msize) {
            nsize = msize;
         }

         arr = new Object[nsize];
         System.arraycopy(oarr, 0, arr, 0, size);
      }
   }

   /**
    * Return the number of elements in this list.
    * @return the number of elements in this list.
    */
   @Override
   public int size() {
      return size;
   }

   /**
    * Check if this list has no elements.
    * @return  <tt>true</tt> if this list has no elements; <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean isEmpty() {
      return size == 0;
   }

   /**
    * Check if this list contains the specified element.
    * @param elem element whose presence in this List is to be tested.
    * @return <tt>true</tt> if the specified element is present; <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean contains(Object elem) {
      return indexOf(elem) >= 0;
   }

   /**
    * Search for the first occurence of the given argument.
    * @param elem an object.
    * @return the index of the first occurrence of the argument in this list;
    * <tt>-1</tt> if not found.
    */
   @Override
   public int indexOf(Object elem) {
      if(elem == null) {
         for(int i = 0; i < size; i++) {
            if(arr[i] == null) {
               return i;
            }
         }
      }
      else {
         for(int i = 0; i < size; i++) {
            if(elem.equals(arr[i])) {
               return i;
            }
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
   @Override
   public int lastIndexOf(Object elem) {
      if(elem == null) {
         for(int i = size - 1; i >= 0; i--) {
            if(arr[i] == null) {
               return i;
            }
         }
      }
      else {
         for(int i = size - 1; i >= 0; i--) {
            if(elem.equals(arr[i])) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Clone this object.
    * @return a clone of this <tt>XObjectList</tt> instance.
    */
   @Override
   public Object clone() {
      try {
         XObjectList v = (XObjectList) super.clone();
         v.arr = new Object[size];
         System.arraycopy(arr, 0, v.arr, 0, size);
         v.modCount = 0;
         return v;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone XObjectList", ex);
      }

      return null;
   }

   /**
    * Return an array containing all of the elements in this list.
    * @return an array containing all of the elements in this list.
    */
   @Override
   public Object[] toArray() {
      Object[] result = new Object[size];
      System.arraycopy(arr, 0, result, 0, size);
      return result;
   }

   /**
    * Return an array containing all of the elements in this list.
    * @param a the array into which the elements of the list are to be stored,
    * if it is big enough; otherwise, a new array of the same runtime type is
    * allocated for this purpose.
    * @return an array containing the elements of the list.
    */
   @Override
   public Object[] toArray(Object[] a) {
      if(a.length < size) {
         Class cls = a.getClass();
         a = (Object[]) Array.newInstance(cls.getComponentType(), size);
      }

      System.arraycopy(arr, 0, a, 0, size);
      return a;
   }

   /**
    * Return the element at the specified position in this list.
    * @param index index of element to return.
    * @return the element at the specified position in this list.
    */
   @Override
   public Object get(int index) {
      return arr[index];
   }

   /**
    * Replace the element at the specified position in this list with
    * the specified element.
    * @param index index of element to replace.
    * @param element element to be stored at the specified position.
    * @return the element previously at the specified position.
    */
   @Override
   public Object set(int index, Object element) {
      Object oldValue = arr[index];
      arr[index] = element;
      return oldValue;
   }

   /**
    * Append the specified element to the end of this list.
    * @param o element to be appended to this list.
    * @return true.
    */
   @Override
   public boolean add(Object o) {
      ensureCapacity(size + 1);
      arr[size++] = o;
      return true;
   }

   /**
    * Insert the specified element at the specified position in this list.
    * @param index index at which the specified element is to be inserted.
    * @param element element to be inserted.
    */
   @Override
   public void add(int index, Object element) {
      ensureCapacity(size + 1);
      System.arraycopy(arr, index, arr, index + 1, size - index);
      arr[index] = element;
      size++;
   }

   /**
    * Remove the element at the specified position in this list.
    * @param index the index of the element to removed.
    * @return the element that was removed from the list.
    */
   @Override
   public Object remove(int index) {
      modCount++;
      Object oldValue = arr[index];
      int numMoved = size - index - 1;

      if(numMoved > 0) {
         System.arraycopy(arr, index + 1, arr, index, numMoved);
      }

      arr[--size] = null;
      return oldValue;
   }

   /**
    * Remove all of the elements from this list.
    */
   @Override
   public void clear() {
      modCount++;

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
   @Override
   public boolean addAll(Collection c) {
      modCount++;
      int numNew = c.size();
      ensureCapacity(size + numNew);
      Iterator e = c.iterator();

      for(int i = 0; i < numNew; i++) {
         arr[size++] = e.next();
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
   @Override
   public boolean addAll(int index, Collection c) {
      int numNew = c.size();
      ensureCapacity(size + numNew);
      int numMoved = size - index;

      if(numMoved > 0) {
         System.arraycopy(arr, index, arr, index + numNew, numMoved);
      }

      Iterator e = c.iterator();

      for(int i = 0; i < numNew; i++) {
         arr[index++] = e.next();
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
   @Override
   protected void removeRange(int fromIndex, int toIndex) {
      modCount++;
      int numMoved = size - toIndex;
      System.arraycopy(arr, toIndex, arr, fromIndex, numMoved);
      int newSize = size - (toIndex - fromIndex);

      while(size != newSize) {
         arr[--size] = null;
      }
   }
   
   /**
    * Get the string representation.
    */
   public String toString() {
      Object[] arr2 = toArray();
      return Arrays.asList(arr2).toString();
   }

   public Object[] arr;
   public int size;
   public int capacity;

   private static final Logger LOG =
      LoggerFactory.getLogger(XObjectList.class);
}
