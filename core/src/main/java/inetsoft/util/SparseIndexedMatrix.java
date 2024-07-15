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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * A memory efficient sparse matrix.
 */
public class SparseIndexedMatrix implements Cloneable, Serializable {
   public SparseIndexedMatrix() {
      clear();
   }

   /**
    * Remove all data in the matrix.
    */
   public void clear() {
      matrix = new int[0][0];
      colIdxs = new int[0];
      rowIdxs = new int[0];
      empty = true;
   }

   /**
    * Set the row attribute.
    */
   public void setRow(int row, Object val) {
      if(row < 0) {
         throw new RuntimeException(row + " is an invalid row index.");
      }

      if(val == null) {
         if(row < rowIdxs.length) {
            rowIdxs[row] = 0;
         }

         return;
      }

      if(row >= rowIdxs.length) {
         rowIdxs = expand(rowIdxs, row + 1);
      }

      rowIdxs[row] = find(val);
      empty = false;
   }

   /**
    * Get the row attribute.
    */
   public Object getRow(int row) {
      if(row < 0 || row >= rowIdxs.length) {
         return null;
      }

      return (rowIdxs[row] == 0) ? null : objects[rowIdxs[row] - 1];
   }

   /**
    * Set the column attribute.
    */
   public void setColumn(int column, Object val) {
      if(column < 0) {
         throw new RuntimeException(column + " is an invalid column index.");
      }

      if(val == null) {
         if(column < colIdxs.length) {
            colIdxs[column] = 0;
         }

         return;
      }

      if(column >= colIdxs.length) {
         colIdxs = expand(colIdxs, column + 1);
      }

      colIdxs[column] = find(val);
      empty = false;
   }

   /**
    * Get the column attribute.
    */
   public Object getColumn(int column) {
      if(column < 0 || column >= colIdxs.length) {
         return null;
      }

      return (colIdxs[column] == 0) ? null : objects[colIdxs[column] - 1];
   }

   /**
    * Set the value of a cell in the matrix.
    */
   public void set(int row, int col, Object val) {
      if(col >= matrix.length) {
         int[][] nmatrix = new int[col + 1][];

         System.arraycopy(matrix, 0, nmatrix, 0, matrix.length);
         matrix = nmatrix;

         for(int i = 0; i < matrix.length; i++) {
            if(matrix[i] == null) {
               matrix[i] = new int[0];
            }
         }
      }

      int[] rowIdxs = matrix[col];

      if(val == null) {
         if(row < rowIdxs.length) {
            rowIdxs[row] = 0;
         }

         return;
      }

      if(row >= rowIdxs.length) {
         matrix[col] = rowIdxs = expand(rowIdxs, row + 1);
      }

      rowIdxs[row] = find(val);
      empty = false;
   }

   /**
    * Get the value of a cell in the matrix. If the cell value has not been
    * set, a NoSuchElementException will be thrown.
    */
   public Object get(int row, int col) {
      if(row < 0 || col < 0 || col >= matrix.length) {
         return null;
      }

      int[] rowIdxs = matrix[col];

      if(row >= rowIdxs.length) {
         return null;
      }

      return (rowIdxs[row] == 0) ? null : objects[rowIdxs[row] - 1];
   }

   /**
    * Get all values set in the matrix. Some items may be null which means the
    * end of the array.
    */
   public Object[] getValues() {
      return objects;
   }

   /**
    * Check if the matrix is empty.
    */
   public boolean isEmpty() {
      return empty;
   }

   /**
    * Insert n rows at idx.
    */
   public void insertRow(int idx, int n) {
      rowIdxs = insert(rowIdxs, idx, n);

      for(int i = 0; i < matrix.length; i++) {
         matrix[i] = insert(matrix[i], idx, n);
      }
   }

   /**
    * Insert n columns at idx.
    */
   public void insertColumn(int idx, int n) {
      colIdxs = insert(colIdxs, idx, n);
      matrix = insert(matrix, idx, n);
   }

   /**
    * Remove n rows at idx.
    */
   public void removeRow(int idx, int n) {
      remove(rowIdxs, idx, n);

      for(int i = 0; i < matrix.length; i++) {
         remove(matrix[i], idx, n);
      }
   }

   /**
    * Remove n columns at idx.
    */
   public void removeColumn(int idx, int n) {
      remove(colIdxs, idx, n);
      remove(matrix, idx, n);
   }

   /**
    * Expand array to the new size.
    */
   private int[] expand(int[] arr, int n) {
      int[] narr = new int[n * 2];

      System.arraycopy(arr, 0, narr, 0, arr.length);
      return narr;
   }

   /**
    * Remove n items from the array at the idx.
    */
   private void remove(int[] arr, int idx, int n) {
      if(idx >= arr.length) {
         return;
      }

      if(idx + n >= arr.length) {
         n = arr.length - idx;
      }

      System.arraycopy(arr, idx + n, arr, idx, arr.length - idx - n);

      for(int i = 0; i < n; i++) {
         arr[arr.length - i - 1] = (int) 0;
      }
   }

   /**
    * Remove n items from the array at the idx.
    */
   private void remove(int[][] arr, int idx, int n) {
      if(idx >= arr.length) {
         return;
      }

      if(idx + n >= arr.length) {
         n = arr.length - idx;
      }

      System.arraycopy(arr, idx + n, arr, idx, arr.length - idx - n);

      for(int i = 0; i < n; i++) {
         arr[arr.length - i - 1] = new int[0];
      }
   }

   /**
    * Insert n items to the array at the idx.
    */
   private int[] insert(int[] arr, int idx, int n) {
      if(idx >= arr.length) {
         return arr;
      }

      int[] narr = new int[arr.length + n];
      System.arraycopy(arr, 0, narr, 0, idx);
      System.arraycopy(arr, idx, narr, idx + n, arr.length - idx);

      return narr;
   }

   /**
    * Insert n items to the array at the idx.
    */
   private int[][] insert(int[][] arr, int idx, int n) {
      if(idx >= arr.length) {
         return arr;
      }

      int[][] narr = new int[arr.length + n][0];
      System.arraycopy(arr, 0, narr, 0, idx);
      System.arraycopy(arr, idx, narr, idx + n, arr.length - idx);

      return narr;
   }

   /**
    * Find or insert an object into the index. Index is based on 1.
    */
   private int find(Object obj) {
      int i = 0;

      for(i = 0; i < objects.length && objects[i] != null; i++) {
         if(objects[i].equals(obj)) {
            return (int) (i + 1);
         }
      }

      if(i >= 126) {
         if(compressObjects()) {
            return find(obj);
         }

         LOG.debug("Too many different types of " +
            obj.getClass());
      }

      if(i >= objects.length) {
         Object[] nobjects = new Object[objects.length + 2];

         System.arraycopy(objects, 0, nobjects, 0, objects.length);
         objects = nobjects;
      }

      objects[i] = obj;
      return (int) (i + 1);
   }

   /**
    * Compress and remove unused objects.
    */
   private boolean compressObjects() {
      BitSet used = new BitSet();

      setMask(used, rowIdxs);
      setMask(used, colIdxs);

      for(int i = 0; i < matrix.length; i++) {
         setMask(used, matrix[i]);
      }

      int idx = 0;
      boolean compressed = false;

      for(int i = 0; i < objects.length; i++) {
         if(objects[i] != null && !used.get(i)) {
            objects[i] = null;
            compressed = true;
         }
      }

      // remove the null values from the array
      if(compressed) {
         Object[] narr = new Object[objects.length];
         Map<Integer, Integer> maskMap = new HashMap<>();

         for(int i = 0, n = 0; i < objects.length; i++) {
            if(objects[i] != null) {
               maskMap.put((int) i, (int) n);
               narr[n++] = objects[i];
            }
         }

         objects = narr;
         // @by davyc, when data compressed, the original index is wrong,
         // reset those mask index
         // fix bug1295861518462, bug1295851727740
         setMask(maskMap, rowIdxs);
         setMask(maskMap, colIdxs);

         for(int i = 0; i < matrix.length; i++) {
            setMask(maskMap, matrix[i]);
         }
      }

      return compressed;
   }

   /**
    * Set the bit of all used index.
    */
   private void setMask(BitSet mask, int[] arr) {
      for(int i = 0; i < arr.length; i++) {
         if(arr[i] > 0) {
            mask.set(arr[i] - 1);
         }
      }
   }

   /**
    * Reset mask after objects compressed.
    */
   private void setMask(Map<Integer, Integer> maskMap, int[] arr) {
      for(int i = 0; i < arr.length; i++) {
         Integer nmask = maskMap.get((int) (arr[i] - 1));

         if(nmask != null) {
            arr[i] = (int) (nmask + 1);
         }
      }
   }

   @Override
   public Object clone() {
      try {
         SparseIndexedMatrix obj = (SparseIndexedMatrix) super.clone();

         if(objects != null) {
            obj.objects = (Object[]) objects.clone();
         }

         if(rowIdxs != null) {
            obj.rowIdxs = (int[]) rowIdxs.clone();
         }

         if(colIdxs != null) {
            obj.colIdxs = (int[]) colIdxs.clone();
         }

         if(matrix != null) {
            obj.matrix = (int[][]) matrix.clone();

            for(int i = 0; i < matrix.length; i++) {
               if(matrix[i] != null) {
                  obj.matrix[i] = (int[]) matrix[i].clone();
               }
            }
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SparseIndexedMatrix", ex);
      }

      return null;
   }

   private Object[] objects = new Object[4];
   private int[] rowIdxs = {};
   private int[] colIdxs;
   private int[][] matrix;
   private boolean empty = true; // no data in the matrix

   private static final Logger LOG =
      LoggerFactory.getLogger(SparseIndexedMatrix.class);
}
