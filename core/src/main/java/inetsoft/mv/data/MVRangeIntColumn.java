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
package inetsoft.mv.data;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwapper;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * A measure column for storing values as int. Values may be converted to
 * a coarse range by dividing by a factor.
 *
 * @author InetSoft Technology
 * @version 12.1
 */
public class MVRangeIntColumn extends MVDecimalColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    * @param factor the factor to convert the value to be stored.
    */
   public MVRangeIntColumn(SeekableInputStream channel, long fpos, BlockFile file,
                           int size, boolean newbuf, double factor)
   {
      super(channel, fpos, file, size, newbuf);
      this.factor = factor;
   }

   /**
    * Get a fragment of the column containing the row.
    * @index fragment index.
    * @return the fragment array value.
    */
   @Override
   public double[] getFragment(int index) {
      return (double[]) getFragment(index, true);
   }

   private Object getFragment(int index, boolean asdouble) {
      int[] arr = ((Fragment2) fragments[index]).access();

      if(!asdouble) {
         return arr;
      }

      double[] arr2 = new double[arr.length];

      for(int i = 0; i < arr.length; i++) {
         int val = arr[i];
         arr2[i] = (val == Tool.NULL_INTEGER) ? Tool.NULL_DOUBLE : val * factor;
      }

      return arr2;
   }

   /**
    * Number of bytes per value.
    */
   @Override
   protected int bytesPer() {
      return 4;
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   @Override
   protected BitSet getRows(double[] vals, boolean not, boolean cnull) {
      BitSet valset = new BitSet();
      BitSet rows = new BitSet();
      int rcnt = getRowCount();
      int min = Integer.MAX_VALUE;
      boolean includeNull = false;

      // @by yanie: bug1414743167906
      // loop to find the min value in vals
      for(int i = 0; i < vals.length; i++) {
         min = min <= vals[i] ? min : ((int) vals[i]);
      }

      // bitmap of the comparison values
      for(int i = 0; i < vals.length; i++) {
         // @by yanie: bug1414743167906
         // the value might be negative, so minus min to avoid negative index
         if(vals[i] == Tool.NULL_DOUBLE) {
            includeNull = true;
         }
         else {
            valset.set((int) (vals[i] - min));
         }
      }

      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         int[] fragment = (int[]) getFragment(fidx, false);
         int cnt = fragment.length;

         for(int n = 0; n < cnt; n++, i++) {
            int dval = fragment[n];
            // @by yanie: bug1414743167906
            // when get, minus min accordingly to value BitSet
            boolean found = includeNull && dval == Tool.NULL_INTEGER ||
               valset.get(dval - min);

            if(not) {
               found = !found;

               // if null is used as comparison, it should always be false
               // as in sql
               if(found && dval == Tool.NULL_INTEGER && !includeNull) {
                  found = false;
               }
            }

            if(found) {
               rows.add(i);
            }
         }
      }

      rows.complete();

      return rows;
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   protected BitSet getRows0(long from0, boolean fincluded, long to0,
                             boolean tincluded, boolean cnull)
   {
      // null (Integer.MIN_VALUE) should be out of the range
      double from = (from0 == Integer.MIN_VALUE) ? Integer.MIN_VALUE + 1
         : getComparisonValue(Double.longBitsToDouble(from0));
      double to = (to0 == Integer.MIN_VALUE) ? Integer.MAX_VALUE
         : getComparisonValue(Double.longBitsToDouble(to0));
      BitSet rows = new BitSet();
      int rcnt = getRowCount();

      // optimization, special handling for common query to avoid
      // additional comparison and casting
      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         int[] farr = (int[]) getFragment(fidx, false);

         if(fincluded) {
            if(tincluded) {
               if(getIncludeNullCompare()) {
                  getRowsBothInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsBothInclusiveExcludeNull(from, to, rows, farr, i);
               }
            }
            else { // if(!tincluded)
               if(getIncludeNullCompare()) {
                  getRowsLeftInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsLeftInclusiveExcludeNull(from, to, rows, farr, i);
               }
            }
         }
         // left not inclusive
         else {
            if(tincluded) {
               if(getIncludeNullCompare()) {
                  getRowsRightInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsRightInclusiveExcludeNull(from, to, rows, farr, i);
               }
            }
            else { // if(!tincluded)
               if(getIncludeNullCompare()) {
                  getRowsNoneInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsNoneInclusiveExcludeNull(from, to, rows, farr, i);
               }
            }
         }

         i += farr.length;
      }

      rows.complete();
      return rows;
   }

   /**
    * Optimization, inclusive on both ends and include null.
    */
   private void getRowsBothInclusiveIncludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if((val < from || val > to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on both ends and exclude null.
    */
   private void getRowsBothInclusiveExcludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if(val < from || val > to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on left and include null.
    */
   private void getRowsLeftInclusiveIncludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if((val < from || val >= to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on left and exclude null.
    */
   private void getRowsLeftInclusiveExcludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if(val < from || val >= to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on right and include null.
    */
   private void getRowsRightInclusiveIncludeNull(double from, double to,
                                                 BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if((val <= from || val > to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on right and exclude null.
    */
   private void getRowsRightInclusiveExcludeNull(double from, double to,
                                                 BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if(val <= from || val > to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on right and include null.
    */
   private void getRowsNoneInclusiveIncludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if((val <= from || val >= to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on right and exclude null.
    */
   private void getRowsNoneInclusiveExcludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         int val = farr[n];

         if(val <= from || val >= to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      // do nothing
   }

   /**
    * Create a column fragment.
    */
   @Override
   protected Fragment createFragment(int index, int size, boolean newbuf) {
      return new Fragment2(index, size, newbuf);
   }

   @Override
   protected double getComparisonValue(double val) {
      return (val == Tool.NULL_DOUBLE) ? val : val / factor;
   }

   protected class Fragment2 extends Fragment {
      public Fragment2(int index, int size, boolean newbuf) {
         super(index, size, newbuf);
      }

      @Override
      public ByteBuffer copyToBuffer(ByteBuffer buf) {
         XSwapUtil.clear(buf);
         int[] arr = access();
         IntBuffer dbuf = buf.asIntBuffer();
         dbuf.put(arr);
         XSwapUtil.position(buf, buf.position() + arr.length * 4);
         XSwapUtil.flip(buf);
         ByteBuffer buf2 = buf;

         if(isCompressed()) {
            buf2 = XSwapUtil.compressByteBuffer(buf);
         }

         return buf2;
      }

      @Override
      public double getValue(int index) {
         int[] arr = access();
         int val = arr[index];

         if(val == Tool.NULL_INTEGER) {
            return Tool.NULL_DOUBLE;
         }
         else {
            return val * factor;
         }
      }

      @Override
      public void setValue(int index, double val) {
         if(arr == null) {
            XSwapper.getSwapper().waitForMemory();
            arr = new int[size];
         }

         arr[index] = (val == Tool.NULL_DOUBLE) ? Tool.NULL_INTEGER
            : (int) (val / factor);
      }

      @Override
      public boolean isValid() {
         return arr != null;
      }

      @Override
      public synchronized boolean swap() {
         if(super.swap()) {
            arr = null;
            return true;
         }

         return false;
      }

      @Override
      public synchronized void dispose() {
         arr = null;
      }

      protected int[] access() {
         iaccessed = XSwapper.cur;
         int[] arr = this.arr;

         if(arr != null) {
            return arr;
         }

         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            arr = this.arr;

            if(arr != null) {
               return arr;
            }

            arr = new int[size];
            ByteBuffer rowBuffer = readBlock(this.index);
            rowBuffer.asIntBuffer().get(arr);
            this.arr = arr;
         }

         return arr;
      }

      // resize buffer to new size
      @Override
      protected void resizeTo(int size) {
         if(arr != null && arr.length > size) {
            int[] arr2 = new int[size];
            System.arraycopy(arr, 0, arr2, 0, size);
            arr = arr2;
         }
      }

      public String toString() {
         return super.toString() + "[" + index + "," + size + "]";
      }

      private int[] arr;
   }

   private double factor = 1;
}
