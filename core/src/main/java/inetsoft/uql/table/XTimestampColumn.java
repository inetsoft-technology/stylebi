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
package inetsoft.uql.table;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.swap.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

/**
 * XTimestampColumn, maintains the meta information and data of one timestamp
 * table column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XTimestampColumn extends AbstractTableColumn {
   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XTimestampColumn(isize, size);
         }

         @Override
         public Class getColType() {
            return java.sql.Timestamp.class;
         }
      };
   }

   /**
    * Create an instance of <tt>XTimestampColumn</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XTimestampColumn(char isize, char size) {
      super();

      this.size = size;
      this.pos = 0;
      this.arr = new long[isize];
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
      return XSchema.TIME_INSTANT;
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
         long[] oarr = arr;
         long[] narr = new long[nsize];
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

      // fix Bug #36081, for tabular date string with format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'".
      if(obj instanceof String) {
         String str = (String) obj;
         obj = null;

         if(!StringUtils.isEmpty(str)) {
            try {
               if(str.endsWith("Z")) {
                  SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                  obj = format.parse(str);
               }
            }
            catch(Exception ex) {
               // ignore
            }

            if(obj == null) {
               try {
                  obj = DateTime.parse(str).toDate();
               }
               catch(Exception ex2) {
                  // joda time has trouble parsing dd-MM-yyyy (47216).
                  try {
                     SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy");
                     obj = format.parse(str);
                  }
                  catch(Exception ex3) {
                     if(logged) {
                        LOG.debug("Parse {0} failed", str, ex2);
                     }
                     else {
                        logged = true;
                        LOG.error("Parse {0} failed", str, ex2);
                     }
                  }
               }
            }
         }
      }

      arr[pos++] = obj == null ? -1 : ((java.util.Date) obj).getTime();

      return null;
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public Object getObject(int r) {
      long[] arr = access();
      return arr[r] == -1 ? null : new java.sql.Timestamp(arr[r]);
   }

   /**
    * Check if the value in one row is null.
    * @param r the specified row index.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r) {
      long[] arr = access();
      return arr[r] == -1;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r) {
      long[] arr = access();
      return arr[r];
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r) {
      long[] arr = access();
      return arr[r];
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r) {
      long[] arr = access();
      return arr[r];
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r) {
      long[] arr = access();
      return (int) arr[r];
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r) {
      long[] arr = access();
      return (short) arr[r];
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r) {
      long[] arr = access();
      return (byte) arr[r];
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r) {
      long[] arr = access();
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
      long[] arr = new long[pos];
      buf.asLongBuffer().get(arr);
      this.arr = arr;
   }

   @Override
   public ByteBuffer copyToBuffer() {
      ByteBuffer buf = ByteBufferPool.getByteBuffer(pos * 8);
      buf.asLongBuffer().put(arr, 0, pos);
      XSwapUtil.limit(buf, pos * 8);
      return buf;
   }

   /**
    * Load the array. It should never be null.
    */
   private final long[] access() {
      long[] arr = this.arr;

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

   private long[] arr;
   private char size;
   private boolean logged = false;
   private static final Logger LOG = LoggerFactory.getLogger(XTimestampColumn.class);
}
