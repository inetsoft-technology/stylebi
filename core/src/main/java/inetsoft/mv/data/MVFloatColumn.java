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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * A double measure column that is swapped to/from block files.
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public class MVFloatColumn extends MVDecimalColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    */
   public MVFloatColumn(SeekableInputStream channel, long fpos, BlockFile file,
      int size, boolean newbuf)
   {
      super(channel, fpos, file, size, newbuf);
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
      float[] arr = (float[]) ((Fragment2) fragments[index]).access();

      if(asdouble) {
         double[] darr = new double[arr.length];

         for(int i = 0; i < arr.length; i++) {
            double val = arr[i];
            darr[i] = (val == Tool.NULL_FLOAT) ? Tool.NULL_DOUBLE : arr[i];
         }

         return darr;
      }

      return arr;
   }

   /**
    * Number of bytes per value.
    */
   @Override
   protected int bytesPer() {
      return 4;
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   protected BitSet getRows0(long from0, boolean fincluded, long to0,
                             boolean tincluded, boolean cnull)
   {
      if(fincluded) {
         float from = (from0 == Integer.MIN_VALUE)
            // make sure NULL is not in range
            ? -Float.MAX_VALUE + Float.MAX_VALUE / 10000000
            : (float) getComparisonValue(Double.longBitsToDouble(from0));
         float to = (to0 == Integer.MIN_VALUE) ? Float.MAX_VALUE
            : (float) getComparisonValue(Double.longBitsToDouble(to0));
         BitSet rows = new BitSet();
         int rcnt = getRowCount();

         // optimization, special handling for common query to avoid
         // additional comparison and casting
         for(int fidx = 0, i = 0; i < rcnt; fidx++) {
            float[] farr = (float[]) getFragment(fidx, false);

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

            i += farr.length;
         }

         rows.complete();
         return rows;
      }

      return super.getRows0(from0, fincluded, to0, tincluded, cnull);
   }

   /**
    * Optimization, inclusive on both ends and include null.
    */
   private void getRowsInclusiveIncludeNull(float from, float to,
                                            BitSet rows, float[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         float val = farr[n];

         if((val < from || val > to) && val != -Float.MAX_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on both ends and exclude null.
    */
   private void getRowsInclusiveExcludeNull(float from, float to,
                                            BitSet rows, float[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         float val = farr[n];

         if(val < from || val > to) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on left and include null.
    */
   private void getRowsLeftInclusiveIncludeNull(float from, float to,
                                                BitSet rows, float[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         float val = farr[n];

         if((val < from || val >= to) && val != -Float.MAX_VALUE) {
            continue;
         }

         rows.add(r);
      }
   }

   /**
    * Optimization, inclusive on left and include null.
    */
   private void getRowsLeftInclusiveExcludeNull(float from, float to,
                                                BitSet rows, float[] farr, int r)
   {
      int cnt = farr.length;

      for(int n = 0; n < cnt; n++, r++) {
         float val = farr[n];

         if(val < from || val >= to) {
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

   private class Fragment2 extends Fragment {
      public Fragment2(int index, int size, boolean newbuf) {
         super(index, size, newbuf);
      }

      @Override
      public ByteBuffer copyToBuffer(ByteBuffer buf) {
         XSwapUtil.clear(buf);
         float[] arr = access();
         FloatBuffer dbuf = buf.asFloatBuffer();
         dbuf.put(arr);
         XSwapUtil.position(buf, arr.length * 4);
         XSwapUtil.flip(buf);
         ByteBuffer buf2 = buf;

         if(isCompressed()) {
            buf2 = XSwapUtil.compressByteBuffer(buf);
         }

         return buf2;
      }

      @Override
      public double getValue(int index) {
         float[] arr = access();
         float val = arr[index];
         return (val == Tool.NULL_FLOAT) ? Tool.NULL_DOUBLE : val;
      }

      @Override
      public void setValue(int index, double val) {
         if(arr == null) {
            XSwapper.getSwapper().waitForMemory();
            arr = new float[size];
         }

         arr[index] = (val == Tool.NULL_DOUBLE) ? Tool.NULL_FLOAT : (float) val;
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

      protected float[] access() {
         iaccessed = XSwapper.cur;
         float[] arr = this.arr;

         if(arr != null) {
            return arr;
         }

         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            arr = this.arr;

            if(arr != null) {
               return arr;
            }

            arr = new float[size];
            ByteBuffer rowBuffer = readBlock(this.index);
            rowBuffer.asFloatBuffer().get(arr);
            this.arr = arr;
         }

         return arr;
      }

      // resize buffer to new size
      @Override
      protected void resizeTo(int size) {
         if(arr != null && arr.length > size) {
            float[] arr2 = new float[size];
            System.arraycopy(arr, 0, arr2, 0, size);
            arr = arr2;
         }
      }

      public String toString() {
         return super.toString() + "[" + index + "," + size + "]";
      }

      private float[] arr;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MVFloatColumn.class);
}
