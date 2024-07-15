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
package inetsoft.report.internal.table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Serializable;
import java.util.BitSet;

/**
 * Class to store cell span information.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class SpanMap implements Cloneable, Serializable {
   /**
    * Add a cell span to the map. This implementation assumes span information
    * is added in the increasing order of row and column.
    */
   public void add(int row, int col, int nrows, int ncols) {
      if(count >= spans.length) {
         SpanContainer[] narr = new SpanContainer[spans.length + INC];
         System.arraycopy(spans, 0, narr, 0, spans.length);
         spans = narr;
      }

      if(count > 0 && spans[count - 1] != null &&
         (spans[count - 1].contains(row) || spans[count - 1].contains(row + nrows - 1)))
      {
         spans[count - 1].add(new Span(row, col, nrows, ncols));
      }
      else {
         spans[count++] = new SpanContainer(new Span(row, col, nrows, ncols));
      }
   }

   /**
    * Set a cell span to the map. This implementation assumes span information
    * is added in the increasing order of row and column.
    */
   /*
   public void set(int row, int col, int nrows, int ncols) {
      int low = 0;
      int high = count - 1;

      while(low <= high) {
         int mid = (low + high) >> 1;
         Span span = spans[mid];
         int cmp = compare(row, col, span);

         if(cmp < 0) {
            high = mid - 1;
         }
         else if(cmp > 0) {
            low = mid + 1;
         }
         else {
            spans[mid].setRows(nrows);
            spans[mid].setCols(ncols);
         }
      }
   }
   */

   /**
    * Find the cell span setting. If this cell is in the middle of a span cell,
    * the Rectangle.x is the negative distance to the leftmost cell in the span
    * cell. Rectangle.y is the negative distance to the topmost cell in the
    * span cell. The width is the span width from this cell to the right of
    * the span cell. The height is the span height from this cell to the bottom
    * of the span cell.
    */
   public Rectangle get(int row, int col) {
      if(row < 0 || col < 0 || count == 0) {
         return null;
      }

      // inmmutable last search to support multi-threading
      LastSearch last = last0;

      if(last != null && row == last.row0) {
         // optimization, in case get() called on same row/col, don't need to
         // check
         if(col == last.col0) {
            return last.span0;
         }
         // if there is no container found for this row, no need to check again
         else if(last.container0 == null) {
            return null;
         }
      }

      int low = 0;
      int high = count - 1;
      // @by larryl, optimization. Since the get() is almost always called
      // sequentially using consecutive row/col number, we set the mid point
      // to the last mid point, which is most likely a direct hit
      int mid = (last != null) ? last.lastMid0 : ((low + high) >> 1);

      while(low <= high) {
         int cmp = compare(row, spans[mid]);

         if(cmp < 0) {
            high = mid - 1;
         }
         else if(cmp > 0) {
            low = mid + 1;
         }
         else {
            Span span = spans[mid].find(row, col);
            last = last == null ? new LastSearch() : (LastSearch) last.clone();
            last.row0 = row;
            last.col0 = col;
            last.lastMid0 = mid;
            last.container0 = spans[mid];

            if(span != null) {
               if(marks != null) {
                  marks.set(mid);
               }

               last.span0 = span.getSpan(row, col);
            }
            else {
               last.span0 = null;
            }

            // inmmutable last search to support multi-threading
            last0 = last;
            return last.span0;
         }

         mid = (low + high) >> 1;
      }

      return null;
   }

   /**
    * Adjust the span map spans.
    */
   public void adjust(int row, int height) {
      if(row < 0) {
         return;
      }

      // inmmutable last search to support multi-threading
      LastSearch last = last1;

      if(last != null && row == last.row0) {
         return;
      }

      int low = 0;
      int high = count - 1;
      // @by larryl, optimization. Since the get() is almost always called
      // sequentially using consecutive row/col number, we set the mid point
      // to the last mid point, which is most likely a direct hit
      int mid = (last != null) ? last.lastMid0 : ((low + high) >> 1);

      while(low <= high) {
         int cmp = compare(row, spans[mid]);

         if(cmp < 0) {
            high = mid - 1;
         }
         else if(cmp > 0) {
            low = mid + 1;
         }
         else {
            spans[mid].adjust(row, height);

            last = last == null ? new LastSearch() : (LastSearch) last.clone();
            last.row0 = row;
            last.lastMid0 = mid;
            last1 = last;

            break;
         }

         mid = (low + high) >> 1;
      }
   }

   /**
    * Clear the content of the map.
    */
   public void clear() {
      count = 0;
   }

   /**
    * Return the number of span defined in the map.
    */
   public int size() {
      return count;
   }

   /**
    * Start marking containers that has been accessed in get().
    */
   public void mark() {
      marks = new BitSet();
   }

   /**
    * Trim all containers that has not been accessed in this span map.
    */
   public void trim() {
      if(marks == null) {
         return;
      }

      // remove the containers that have not been marked
      for(int i = count - 1; i >= 0; i--) {
         if(!marks.get(i)) {
            int j = marks.previousSetBit(i - 1) + 1;
            int diff = i - j + 1;

            if(i < count - 1) {
               System.arraycopy(spans, i + 1, spans, j, count - i - 1);
            }

            count -= diff;
            i = j - 1; // we know j - 1 is marked
         }
      }

      // create a new array to fit the size
      if(count < spans.length) {
         SpanContainer[] arr = new SpanContainer[count];
         System.arraycopy(spans, 0, arr, 0, arr.length);
         spans = arr;
      }

      marks = null;
      last0 = null;
   }

   /**
    * Make a copy of the map.
    */
   @Override
   public Object clone() {
      try {
         SpanMap map = (SpanMap) super.clone();
         map.spans = (SpanContainer[]) spans.clone();
         map.marks = null;
         map.last0 = null;

         return map;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Exact comparison.
    */
   private int compare(int r, SpanContainer span) {
      if(r < span.top) {
         return -1;
      }
      else if(r > span.bottom) {
         return 1;
      }

      return 0;
   }

   static final class LastSearch implements Cloneable {
      public LastSearch() {
      }

      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            // ignore it
         }

         return null;
      }

      private int row0;
      private int col0;
      private Rectangle span0;
      private int lastMid0;
      private SpanContainer container0;
   }

   /**
    * Span container holds span setting for vertically overlapping spans.
    * It is used to separate the spans into smaller groups for binary search.
    */
   static final class SpanContainer implements Serializable {
      public SpanContainer(Span span) {
         spans = new Span[] {span};
         top = span.getRow();
         bottom = span.getRow() + span.getRows() - 1;
      }

      public void add(Span span) {
         // expand spans by 2 instead of larger amount to conserve memory
         if(spans[spans.length - 1] != null) {
            Span[] nspans = new Span[spans.length + 2];
            System.arraycopy(spans, 0, nspans, 0, spans.length);

            spans = nspans;
            spans[spans.length - 2] = span;
         }
         else {
            spans[spans.length - 1] = span;
         }

         update(span);
      }

      public boolean contains(int r) {
         return r >= top && r <= bottom;
      }

      public Span find(int row, int col) {
         for(int i = 0; i < spans.length && spans[i] != null; i++) {
            if(spans[i].contains(row, col)) {
               return spans[i];
            }
         }

         return null;
      }

      public void adjust(int row, int height) {
         if(contains(row)) {
            for(int i = 0; i < spans.length && spans[i] != null; i++) {
               if(spans[i].contains(row)) {
                  spans[i].adjust(height);
                  update(spans[i]);
               }
            }
         }
      }

      private void update(Span span) {
         top = Math.min(top, span.getRow());
         bottom = Math.max(bottom, span.getRow() + span.getRows() - 1);
      }

      public final int getTop() {
         return top;
      }

      public final int getBottom() {
         return bottom;
      }

      private int top = 0, bottom = Integer.MAX_VALUE;
      private Span[] spans;
   }

   static class Span implements Serializable {
      public Span(int row, int col, int nrow, int ncol) {
         this.row = row;
         this.col = col;
         this.nrow = (short) nrow;
         this.ncol = (short) ncol;
      }

      public int getRow() {
         return row;
      }

      public int getCol() {
         return col;
      }

      public int getRows() {
         return nrow;
      }

      public int getCols() {
         return ncol;
      }

      public void setRows(int nrow) {
         this.nrow = (short) nrow;
      }

      public void setCols(int ncol) {
         this.ncol = (short) ncol;
      }

      public void adjust(int height) {
         this.nrow += height;
      }

      public boolean contains(int r, int c) {
         return r >= row && c >= col && row + nrow > r && col + ncol > c;
      }

      public boolean contains(int r) {
         return r >= row && row + nrow > r;
      }

      /**
       * Get the span for the specified cell. The span is adjusted so it
       * is relative to the specified cell location.
       */
      public Rectangle getSpan(int row0, int col0) {
         return new Rectangle(col - col0, row - row0, col + ncol - col0,
                              row + nrow - row0);
      }

      public String toString() {
         return "Span[" + row + "," + col + " " + nrow + " " + ncol + "]";
      }

      private int row, col;
      private short nrow, ncol;
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();
      last0 = null;
   }

   private static final int INC = 50;
   private SpanContainer[] spans = new SpanContainer[INC];
   private int count = 0; // number of span container
   private transient BitSet marks; // mark which container is accessed
   private transient LastSearch last0; // last search
   private transient LastSearch last1; // last adjust

   private static final Logger LOG =
      LoggerFactory.getLogger(SpanMap.class);
}

