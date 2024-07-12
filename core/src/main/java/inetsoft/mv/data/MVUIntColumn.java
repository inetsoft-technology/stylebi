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
package inetsoft.mv.data;

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.util.SeekableInputStream;
import inetsoft.util.Tool;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A unsigned int measure column that is swapped to/from block files.
 *
 * @author InetSoft Technology
 * @version 12.2
 */
public class MVUIntColumn extends MVDecimalColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    * @param factor the factor to convert the value to be stored.
    */
   public MVUIntColumn(SeekableInputStream channel, long fpos, BlockFile file,
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

   /**
    * Get the fragment array.
    * @param asdouble true to convert the float[] to double[].
    */
   protected Object getFragment(int index, boolean asdouble) {
      int[] arr = (int[]) ((Fragment2) fragments[index]).access();

      if(asdouble) {
         double[] darr = new double[arr.length];

         for(int i = 0; i < arr.length; i++) {
            double val = arr[i];
            darr[i] = (val == NULL_UINT) ? Tool.NULL_DOUBLE
               : (((long) arr[i]) & 0xFFFFFFFFL) * factor;
         }

         return darr;
      }

      return arr;
   }

   /**
    * Number of bytes per value.
    */
   @Override
   protected final int bytesPer() {
      return 4;
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   @Override
   protected BitSet getRows(double[] vals, boolean not, boolean cnull) {
      BitSet rows = new BitSet();
      int rcnt = getRowCount();

      for(int fidx = 0, i = 0; i < rcnt; fidx++) {
         int[] fragment = (int[]) getFragment(fidx, false);
         int cnt = fragment.length;

         for(int n = 0; n < cnt; n++, i++) {
            int ival = fragment[n];
            boolean found = false;

            for(int k = 0; k < vals.length; k++) {
               if(ival == vals[k]) {
                  found = true;
                  break;
               }
            }

            if(not) {
               found = !found;

               // if null is used as comparison, it should always be false
               // as in sql
               if(found && ival == NULL_UINT) {
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
      double from = (from0 == Integer.MIN_VALUE)
         // make sure NULL is not in range
         ? 0.1
         : getComparisonValue(Double.longBitsToDouble(from0));
      double to = (to0 == Integer.MIN_VALUE) ? Double.MAX_VALUE
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
                  getRowsInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsInclusiveExcludeNull(from, to, rows, farr, i);
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
         else {
            if(tincluded) { // if(!fincluded)
               if(getIncludeNullCompare()) {
                  getRowsRightInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsRightInclusiveExcludeNull(from, to, rows, farr, i);
               }
            }
            else { // if(!tincluded && !fincluded)
               if(getIncludeNullCompare()) {
                  getRowsNotInclusiveIncludeNull(from, to, rows, farr, i);
               }
               else {
                  getRowsNotInclusiveExcludeNull(from, to, rows, farr, i);
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
   private void getRowsInclusiveIncludeNull(double from, double to,
                                            BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

         if((val < from || val > to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on both ends and exclude null.
    */
   private void getRowsInclusiveExcludeNull(double from, double to,
                                            BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

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
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

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
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

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
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

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
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

         if(val <= from || val > to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, not inclusive and include null.
    */
   private void getRowsNotInclusiveIncludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

         if((val <= from || val >= to) && val != Integer.MIN_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, not inclusive and and exclude null.
    */
   private void getRowsNotInclusiveExcludeNull(double from, double to,
                                                BitSet rows, int[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         double val = ((long) farr[n]) & 0xFFFFFFFFL;

         if(val <= from || val >= to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Read from channel.
    */
   protected void read0(ReadableByteChannel channel) throws Exception {
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
         return (val == NULL_UINT) ? Tool.NULL_DOUBLE
            : (((long) val) & 0xFFFFFFFFL) * factor;
      }

      @Override
      public void setValue(int index, double val) {
         if(arr == null) {
            XSwapper.getSwapper().waitForMemory();
            arr = new int[size];
         }

         arr[index] = (val == Tool.NULL_DOUBLE) ? NULL_UINT
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

   // this means we don't support 0 in uint. this is OK for now since it's
   // only used for timestamp and epoc can be treated as an invalid date.
   // in the future if we need to support 0, we need to change this to
   // a special value, and then handle the null is getRows() range with
   // an explicit comparison
   private static final int NULL_UINT = 0;

   private double factor = 1;
   private static final Logger LOG = LoggerFactory.getLogger(MVUIntColumn.class);
}
