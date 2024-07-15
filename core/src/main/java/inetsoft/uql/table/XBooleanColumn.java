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

import inetsoft.uql.schema.XSchema;
import inetsoft.util.swap.*;

import java.nio.ByteBuffer;

/**
 * XBooleanColumn, maintains the meta information and data of one boolean table
 * column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XBooleanColumn extends AbstractTableColumn {
   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XBooleanColumn(isize, size);
         }

         @Override
         public Class getColType() {
            return Boolean.class;
         }
      };
   }

   /**
    * Create an instance of <tt>XBooleanColumn</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XBooleanColumn(char isize, char size) {
      super();

      this.size = size;
      this.pos = 0;
      this.arr = new byte[isize];
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
      return XSchema.BOOLEAN;
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
         byte[] oarr = arr;
         byte[] narr = new byte[nsize];
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
      arr[pos++] = obj == null ? NULL :
         (((Boolean) obj).booleanValue() ? TRUE : FALSE);

      return null;
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public Object getObject(int r) {
      byte[] arr = access();
      return arr[r] == NULL ? null : (arr[r] == TRUE);
   }

   /**
    * Check if the value in one row is null.
    * @param r the specified row index.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r) {
      byte[] arr = access();
      return arr[r] == NULL;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r) {
      byte[] arr = access();
      return arr[r] == TRUE ? 1 : 0;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r) {
      byte[] arr = access();
      return arr[r] == TRUE ? 1 : 0;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r) {
      byte[] arr = access();
      return arr[r] == TRUE ? 1 : 0;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r) {
      byte[] arr = access();
      return arr[r] == TRUE ? 1 : 0;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r) {
      byte[] arr = access();
      return arr[r] == TRUE ? (short) 1 : (short) 0;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r) {
      byte[] arr = access();
      return arr[r] == NULL ? (byte) 0 : arr[r];
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r) {
      byte[] arr = access();
      return arr[r] == TRUE;
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

   @Override
   public void copyFromBuffer(ByteBuffer buf) {
      byte[] arr = new byte[pos];
      buf.get(arr);
      this.arr = arr;
   }

   @Override
   public ByteBuffer copyToBuffer() {
      ByteBuffer buf = ByteBufferPool.getByteBuffer(pos * 1);
      buf.put(arr, 0, pos);
      XSwapUtil.flip(buf);
      //buf.limit(pos * 1);
      return buf;
   }

   /**
    * Load the array. It should never be null.
    */
   private final byte[] access() {
      byte[] arr = this.arr;

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

   private static final byte TRUE = (byte) 1;
   private static final byte FALSE = (byte) 0;
   private static final byte NULL = (byte) -1;

   private byte[] arr;
   private char size;
}
