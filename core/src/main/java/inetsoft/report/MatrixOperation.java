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
package inetsoft.report;

import java.awt.*;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * MatrixOperation, operator to operate matrix like operation.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
class MatrixOperation {
   /**
    * Change the size of the array to the specified size.
    */
   public static int[] toSize(int[] arr, int nrow) {
      if(arr.length != nrow) {
         int[] narr = new int[nrow];
         System.arraycopy(arr, 0, narr, 0, Math.min(arr.length, nrow));

         if(narr.length > arr.length) {
            // initialize new items
            for(int i = arr.length; i < narr.length; i++) {
               narr[i] = -1;
            }
         }

         arr = narr;
      }

      return arr;
   }

   /**
    * Insert a new item at the specified position.
    */
   public static int[] insertRow(int[] arr, int row) {
      int[] narr = new int[arr.length + 1];
      narr[row] = -1;
      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row, narr, row + 1, arr.length - row);

      return narr;
   }

   /**
    * remove an item at the specified position.
    */
   public static int[] removeRow(int[] arr, int row) {
      int[] narr = new int[arr.length - 1];

      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row + 1, narr, row, arr.length - row - 1);

      return narr;
   }

   /**
    * Insert a new item at the specified position.
    */
   public static int[] insertColumn(int[] arr, int col) {
      return insertRow(arr, col);
   }

   /**
    * remove an item at the specified position.
    */
   public static int[] removeColumn(int[] arr, int col) {
      return removeRow(arr, col);
   }

   /**
    * Change the array to be the same size as the row and column count.
    */
   public static Object[] toSize(Object[] arr, int nrow) {
      int row = arr.length;
      Class cls = getItemComponentType(arr);
      Object[] narr = (Object[]) Array.newInstance(cls, nrow);
      System.arraycopy(arr, 0, narr, 0, Math.min(nrow, row));
      return arr;
   }

   /**
    * Change the array to be the same size as the row and column count.
    */
   public static <T> T[][] toSize(T[][] arr, int nrow, int ncol) {
      if(arr.length != nrow || arr.length > 0 && arr[0].length != ncol) {
         Class<?> cls =  getItemComponentType(arr);
         T[][] narr = (T[][]) Array.newInstance(cls, nrow, ncol);

         for(int i = 0; i < Math.min(narr.length, arr.length); i++) {
            System.arraycopy(arr[i], 0, narr[i], 0,
                             Math.min(arr[i].length, narr[i].length));
         }

         return narr;
      }

      return arr;
   }

   /**
    * Insert a column before the specified column.
    */
   public static Object[] insertColumn(Object[] arr, int col) {
      Object[] narr = (Object[]) Array.newInstance(
         getItemComponentType(arr), arr.length + 1);
      System.arraycopy(arr, 0, narr, 0, col);
      System.arraycopy(arr, col, narr, col + 1, arr.length - col);
      return narr;
   }

   /**
    * Remove a column from a two dimensional array.
    */
   public static Object[] removeColumn(Object[] arr, int col) {
      Object[] narr = (Object[]) Array.newInstance(
         getItemComponentType(arr), arr.length - 1);
      System.arraycopy(arr, 0, narr, 0, col);
      System.arraycopy(arr, col + 1, narr, col, arr.length - col - 1);
      return narr;
   }

   /**
    * Insert a column before the specified column.
    */
   public static void insertColumn(Object[][] arr, int col) {
      for(int i = 0; i < arr.length; i++) {
         Object[] narr = (Object[]) Array.newInstance(
            getItemComponentType(arr), arr[i].length + 1);
         System.arraycopy(arr[i], 0, narr, 0, col);
         System.arraycopy(arr[i], col, narr, col + 1, arr[i].length - col);
         arr[i] = narr;
      }
   }

   /**
    * Remove a column from a two dimensional array.
    */
   public static void removeColumn(Object[][] arr, int col) {
      for(int i = 0; i < arr.length; i++) {
         Object[] narr = (Object[]) Array.newInstance(
            getItemComponentType(arr), Math.max(0, arr[i].length - 1));
         System.arraycopy(arr[i], 0, narr, 0, col);
         System.arraycopy(arr[i], col + 1, narr, col, arr[i].length - col - 1);
         arr[i] = narr;
      }
   }

   /**
    * Insert a new row above the specified row.
    */
   public static Object[] insertRow(Object[] arr, int row) {
      Object[] narr = (Object[]) Array.newInstance(
         getItemComponentType(arr), arr.length + 1);
      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row, narr, row + 1, arr.length - row);
      return narr;
   }

   /**
    * remove a row from the two dimensional array.
    */
   public static Object[] removeRow(Object[] arr, int row) {
      Object[] narr = (Object[]) Array.newInstance(
         getItemComponentType(arr), arr.length - 1);
      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row + 1, narr, row, arr.length - row - 1);
      return narr;
   }

   /**
    * Insert a new row above the specified row.
    */
   public static Object[][] insertRow(Object[][] arr, int row) {
      Object[][] narr = (Object[][]) Array.newInstance(
         getItemComponentType(arr), new int[] {arr.length + 1,
            arr.length > 0 ? arr[0].length : 0});

      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row, narr, row + 1, arr.length - row);

      narr[row] = (Object[]) Array.newInstance(
         getItemComponentType(arr), arr.length > 0 ? arr[0].length : 0);

      return narr;
   }

   /**
    * remove a row from the two dimensional array.
    */
   public static Object[][] removeRow(Object[][] arr, int row) {
      Object[][] narr = (Object[][]) Array.newInstance(
         getItemComponentType(arr), new int[] {arr.length - 1,
         arr.length > 0 ? arr[0].length : 0});

      System.arraycopy(arr, 0, narr, 0, row);
      System.arraycopy(arr, row + 1, narr, row, arr.length - row - 1);

      return narr;
   }

   /**
    * Set the cell span setting for the cell.
    * @param spans the specifield spans to be operated on.
    * @param ccnt the column count.
    * @param rcnt the row count.
    * @param r global row index.
    * @param c global column index.
    * @param span span.width is the number of columns, and span.height is
    * the number of rows.
    */
   public static void setSpan(Dimension[][] spans, int ccnt, int rcnt,
                              int r, int c, Dimension span)
   {
      Rectangle rect = span == null ? new Rectangle(c, r, 1, 1) :
         new Rectangle(c, r, span.width, span.height);

      // make sure the span does not go out of bound
      if(span != null) {
         // @by humming, this check should be on SpanHelper
         // span.width = Math.min(span.width, ccnt - c);
         // span.height = Math.min(span.height, rcnt - r);

         boolean loop = true;

         // check to merge all overlapping spans
         while(loop) {
            loop = false;

            for(int i = 0; i < spans.length; i++) {
               for(int j = 0; j < spans[i].length; j++) {
                  if(spans[i][j] != null) {
                     Rectangle rect2 = new Rectangle(j, i, spans[i][j].width,
                                                     spans[i][j].height);
                     if(rect.intersects(rect2) && !rect.equals(rect2)) {
                        // merged again? rect changed, original not intersected
                        // span may be intersect again
                        loop = true;
                        // the new span is the two spans union dimension
                        rect = rect.union(rect2);
                        spans[i][j] = null;
                     }
                  }
               }
            }
         }
      }

      span = new Dimension(rect.width, rect.height);
      r = rect.y;
      c = rect.x;
      
      // make sure in bounds
      if(span != null) {
         span.width = Math.min(span.width, ccnt - c);
         span.height = Math.min(span.height, rcnt - r);
      }

      // 1x1 span is meaningless
      if(span.width == 1 && span.height == 1 || span.width <= 0 ||
         span.height <= 0)
      {
         span = null;
      }

      spans[r][c] = span;
   }

   /**
    * Check all span setting. If a span covers the specified row or column,
    * change the span by the adjustment amount.
    * @param spans the specified span need to be adjusted.
    * @param row the row index to be adjusted, if insert/remove column, the
    *  value is -1.
    * @param col the column index to be adjusted, if insert/remove row, the
    *  value is -1.
    * @param rowadj the row adjust count.
    * @param coladj the column adjust count.
    */
   public static void adjustSpan(Dimension[][] spans, int row, int col,
                                 int rowadj, int coladj) {
      for(int i = 0; i < spans.length; i++) {
         for(int j = 0; j < spans[i].length; j++) {
            if(spans[i][j] != null) {
               // @by henryh, use absolute value.
               Rectangle rect = new Rectangle(j + Math.abs(coladj),
                  i + Math.abs(rowadj), spans[i][j].width - Math.abs(coladj),
                  spans[i][j].height - Math.abs(rowadj));

               Point pt = new Point(col, row);

               if(col < 0) {
                  pt.x = j;
               }
               else if(row < 0) {
                  pt.y = i;
               }

               if(rect.contains(pt)) {
                  spans[i][j].width += coladj;
                  spans[i][j].height += rowadj;
               }
            }
         }
      }
   }

   /**
    * Clone int array.
    */
   public static int[] clone(int[] src) {
      int[] target = new int[src.length];
      System.arraycopy(src, 0, target, 0, src.length);
      return target;
   }

   /**
    * Clone object array.
    */
   public static Object[][] clone(Object[][] src) {
      if(src.length <= 0 || src[0].length <= 0) {
         return src;
      }

      Object[][] target = (Object[][]) Array.newInstance(
         getItemComponentType(src), new int[] {src.length,
         src.length > 0 ? src[0].length : 0});

      for(int i = 0; i < target.length; i++) {
         target[i] = clone(src[i]);
      }

      return target;
   }

   /**
    * Clone object array.
    */
   public static Object[] clone(Object[] src) {
      return clone(src, null);
   }

   /**
    * Clone object array.
    */
   public static Object[] clone(Object[] src, Object param) {
      if(src.length <= 0) {
         return src;
      }

      Object[] target = (Object[]) Array.newInstance(
         getItemComponentType(src), src.length);

      for(int i = 0; i < src.length; i++) {
         Object cobj = null;

         if(src[i] != null) {
            try {
               if(param != null) {
                  Method[] methods = src[i].getClass().getMethods();

                  for(int j = 0; j < methods.length; j++) {
                     if("clone".equals(methods[j].getName())) {
                        if(methods[j].getParameterTypes().length > 0) {
                           cobj = methods[j].invoke(src[i],
                              new Object[] {param});
                        }
                     }
                  }
               }

               if(cobj == null) {
                  Method cm = src[i].getClass().getMethod("clone");
                  cobj = cm.invoke(src[i]);
               }
            }
            catch(Exception ex) {
               // ignore it
            }
         }

         target[i] = cobj;
      }

      return target;
   }

   /**
    * Get the item type of an array or multi-dimensional array.
    */
   private static Class<?> getItemComponentType(Object arr) {
      Class<?> cls = arr.getClass();

      while(cls.isArray()) {
         cls = cls.getComponentType();
      }

      return cls;
   }
}
