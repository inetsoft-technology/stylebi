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
package inetsoft.uql.table;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

/**
 * XBDDoubleColumn, maintains the meta information and data of one big decimal
 * table column. One big integer value will be converted to one double value.
 * Note that this conversion can lose information. The scale information will
 * be maintained using XFormatInfo in XTable.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XBDDoubleColumn extends AbstractTableColumn {
   /**
    * Convert a big decimal value to double value.
    * @param bd the specified big decimal value.
    * @return the associated double value.
    */
   private static double toDouble(BigDecimal bd) {
      int scale = bd.scale();

      if(scale == 0) {
         BigInteger bi = bd.unscaledValue();
         long val = bi.longValue();
         return val;
      }
      else if(scale > -10 && scale < 10) {
         BigInteger bi = bd.unscaledValue();
         long val = bi.longValue();

         if(scale > 0) {
            return val / SCALE_ARRAY[scale];
         }
         else {
            return val * SCALE_ARRAY[-scale];
         }
      }
      else {
         return bd.doubleValue();
      }
   }

   /**
    * Check if this column should be used for big decimal.
    */
   public static boolean isRecommended() {
      return recommended;
   }

   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XBDDoubleColumn(isize, size);
         }

         @Override
         public Class getColType() {
            return Double.class;
         }
      };
   }

   /**
    * Create an instance of <tt>XBDDoubleColumn</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XBDDoubleColumn(char isize, char size) {
      super();

      this.size = size;
      this.pos = 0;
      this.arr = new double[isize];
   }

   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getCreator0() {
      return getCreator();
   }

   /**
    * Get the data type.
    * @return the data type.
    */
   @Override
   public String getType() {
      return XSchema.DOUBLE;
   }

   /**
    * Check if data is stored as primitive value.
    * @return <tt>true</tt> if data is stored as primitive value,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimitive() {
      return true;
   }

   /**
    * Get the capacity of this table column.
    * @return the capacity of this table column.
    */
   @Override
   public int capacity() {
      return size;
   }

   /**
    * Ensure capacity.
    */
   private void ensureCapacity() {
      if(pos == arr.length) {
         int nsize = Math.min((int) (arr.length * 1.5), size);
         double[] oarr = arr;
         double[] narr = new double[nsize];
         System.arraycopy(oarr, 0, narr, 0, oarr.length);
         arr = narr;
      }
   }

   /**
    * Add an object.
    * @param obj the specified object.
    * @return the preferred table column.
    */
   @Override
   public XTableColumn addObject(Object obj) {
      ensureCapacity();

      if(obj == null) {
         arr[pos++] = Tool.NULL_DOUBLE;
      }
      else {
         BigDecimal bd = Tool.convertToBigDecimal(obj);

         if(lflag) {
            lflag = bd.scale() == 0;
         }

         arr[pos++] = toDouble(bd);
      }

      return null;
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public Object getObject(int r) {
      if(isNull(r)) {
         return null;
      }
      else if(lflag) {
         double[] arr = access();
         return (long) arr[r];
      }

      double[] arr = access();
      return arr[r];
   }

   /**
    * Check if the value in one row is null.
    * @param r the specified row index.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r) {
      double[] arr = access();
      return arr[r] == Tool.NULL_DOUBLE;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r) {
      double[] arr = access();
      return arr[r];
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r) {
      double[] arr = access();
      return (float) arr[r];
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r) {
      double[] arr = access();
      return (lflag && isNull(r)) ? Tool.NULL_LONG : (long) arr[r];
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r) {
      double[] arr = access();
      return (int) arr[r];
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r) {
      double[] arr = access();
      return (short) arr[r];
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r) {
      double[] arr = access();
      return (byte) arr[r];
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r) {
      double[] arr = access();
      return arr[r] != 0;
   }

   /**
    * Check if the table column is in valid state.
    * @return <tt>true</tt> if in valid state, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return arr != null;
   }

   /**
    * Check if is serializable.
    * @return <tt>true</tt> if serializable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSerializable() {
      return true;
   }

   @Override
   public void copyFromBuffer(ByteBuffer buf) {
      double[] arr = new double[pos];
      buf.asDoubleBuffer().get(arr);
      this.arr = arr;
   }

   @Override
   public ByteBuffer copyToBuffer() {
      ByteBuffer buf = ByteBufferPool.getByteBuffer(pos * 8);
      buf.asDoubleBuffer().put(arr, 0, pos);
      XSwapUtil.limit(buf, pos * 8);
      return buf;
   }

   /**
    * Dispose the serializable.
    */
   @Override
   public void dispose() {
      arr = null;
   }

   /**
    * Complete this table column.
    */
   @Override
   public void complete() {
      return;
   }

   /**
    * Check if the column is long type.
    */
   public boolean isLong() {
      return lflag;
   }

   /**
    * Set long flag.
    */
   public void setLong(boolean flag) {
      this.lflag = flag;
   }

   /**
    * Load the array. It should never be null.
    */
   private final double[] access() {
      double[] arr = this.arr;

      if(arr == null) {
         // waitForMemory outside of synchornized block to avoid deadlock
         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            arr = this.arr;

            if(arr == null) {
               load();
               arr = this.arr;
            }
         }
      }

      return arr;
   }

   @Override
   public synchronized void invalidate() {
      this.arr = null;
   }

   @Override
   public int getInMemoryLength() {
      return access().length;
   }

   private static final double[] SCALE_ARRAY = new double[] {1.0, 10.0, 100.0,
      1000.0, 10000.0, 100000.0, 1000000.0, 10000000.0, 100000000.0,
      1000000000.0};
   private static boolean recommended;

   static {
      recommended = "true".equals(
         SreeEnv.getProperty("bigDecimal.as.double"));
   }

   private double[] arr;
   private char size;
   private boolean lflag = false;
}
