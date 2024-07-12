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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 *
 * @author InetSoft Technology
 * @version 11.1
 */
public class MVIntColumn extends DictDimIndex implements MVMeasureColumn {
   /**
    * Measure column for creating the column (in-memory).
    */
   public MVIntColumn(int size, Number min0, Number max0) {
      super(getBits(min0, max0));

      if(min0 != null) {
         // leave min as null value
         min = min0.intValue() - 1;
         hasMin = true;
      }

      if(max0 != null) {
         max = max0.intValue();
      }

      XSwapper.getSwapper().waitForMemory();
      dimbuf.setSize(size, true);
   }

   /**
    * Measure column for loading the data on demand.
    */
   public MVIntColumn(SeekableInputStream channel, long fpos, BlockFile file,
      int size, int bits)
   {
      super(bits);

      try {
         init(channel, fpos, file, false);
      }
      catch(IOException ex) {
         throw new RuntimeException(ex);
      }

      dimbuf.setSize(size, false);
   }

   /**
    * Set the number of items in the column.
    */
   @Override
   public void setRowCount(int rcnt) {
      int nkeys = dimbuf.getSize();

      if(nkeys == rcnt) {
         return;
      }
      else if(nkeys < rcnt) {
         throw new RuntimeException("Rows can only be reduced in MVIntColumn");
      }

      dimbuf.setSize(rcnt, true);
   }

   /**
    * Get the number of bits necessary for the range.
    */
   private static int getBits(Number min, Number max) {
      if(min == null) {
         return 32;
      }

      int maxV = max.intValue();
      int minV = min.intValue() - 1;

      return (int) Math.ceil(Math.log(maxV - minV + 1) / Math.log(2)) + 1;
   }

   /**
    * Get the column value as a dimention.
    */
   @Override
   public final long getDimValue(int idx) {
      double value = getValue(idx);

      if(value == Tool.NULL_DOUBLE) {
         return Tool.NULL_LONG;
      }

      return (long) value;
   }

   /**
    * Get the column value as a measure.
    */
   @Override
   public final double getMeasureValue(int idx) {
      return getValue(idx);
   }

   /**
    * Get the value at the specified index.
    */
   @Override
   public final double getValue(int idx) {
      int result = dimbuf.getValue(idx);
      accessed = XSwapper.cur;

      if(hasMin) {
         if(result == 0) {
            return Tool.NULL_DOUBLE;
         }
      }
      else if(result == Tool.NULL_INTEGER) {
         return Tool.NULL_DOUBLE;
      }

      return result + min;
   }

   /**
    * Set the value at the specified index.
    */
   @Override
   public void setValue(int idx, double value) {
      int val;

      if(value == Tool.NULL_DOUBLE) {
         val = hasMin ? 0 : Tool.NULL_INTEGER;
      }
      else {
         val = (int) (value - min);
      }

      dimbuf.setValue(idx, val);
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   @Override
   protected BitSet getRows(long[] vals, boolean not, boolean cnull) {
      long[] vals2 = new long[vals.length];

      for(int i = 0; i < vals.length; i++) {
         vals2[i] = getInternalValue(vals[i]);
      }

      // if hasMin, the 0 corresponds to null as in dictionary
      return super.getRows(vals2, not, hasMin);
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   public BitSet getRows(long from, boolean fincluded, long to,
                         boolean tincluded, boolean cnull)
   {
      from = (from == Integer.MIN_VALUE) ? (hasMin ? 1 : 0)
         : (int) getInternalValue(from);
      to = (to == Integer.MIN_VALUE) ? Integer.MAX_VALUE
         : (int) getInternalValue(to);
      return super.getRows(from, fincluded, to, tincluded, cnull);
   }

   /**
    * Get bit set for the like op.
    */
   @Override
   public BitSet getLikeOpRows(String op, long val, boolean not) {
      BitSet set = new BitSet();
      long lval = (long) Double.longBitsToDouble(val);

      if(val == Tool.NULL_LONG) {
         return set;
      }

      String slval = lval + "";
      int nkeys = dimbuf.getSize();

      for(int i = 0; i < nkeys; i++) {
         double rval = getValue(i);

         if(rval == Tool.NULL_DOUBLE) {
            continue;
         }

         String srval = ((int) rval) + "";
         boolean found = not;

         switch(op) {
         case "STARTSWITH":
            if(srval.startsWith(slval)) {
               found = !found;
            }
            break;
         case "CONTAINS":
         case "LIKE": // number contains no wildcard so is same as contains
            if(srval.contains(slval)) {
               found = !found;
            }
            break;
         }

         if(found) {
            set.add(i);
         }
      }

      set.complete();

      return set;
   }

   /**
    * Get the value stored in the dimbuf.
    */
   private long getInternalValue(long value) {
      double val = Double.longBitsToDouble(value);

      if(val == Tool.NULL_DOUBLE) {
         return hasMin ? 0 : Tool.NULL_LONG;
      }
      else {
         return (long) val - min;
      }
   }

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      super.read0(channel);

      ByteBuffer buf = ByteBuffer.allocate(9);
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      min = buf.getInt();
      max = buf.getInt();
      hasMin = buf.get() == 1;
   }

   /**
    * Write to channel.
    */
   @Override
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
         throws IOException
   {
      sbuf = super.write(channel, sbuf);
      ByteBuffer buf = ByteBuffer.allocate(9);

      buf.putInt(min);
      buf.putInt(max);
      buf.put((byte) (hasMin ? 1 : 0));
      XSwapUtil.flip(buf);

      while(buf.hasRemaining()) {
         channel.write(buf);
      }

      return sbuf;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return super.getLength() + 8 + 1;
   }

   /**
    * Get header length.
    */
   @Override
   public int getHeaderLength() {
      return super.getHeaderLength() + 9;
   }

   /**
    * Get the min value.
    */
   public Number min() {
      int minVal = min;

      if(hasMin) {
         minVal += 1;
      }

      return !hasMin && minVal == 0 ? null : Integer.valueOf(minVal);
   }

   /**
    * Get the max value.
    */
   public Number max() {
      return Integer.valueOf(max);
   }

   private int min;
   private int max;
   private boolean hasMin;

   private static final Logger LOG = LoggerFactory.getLogger(MVIntColumn.class);
}
