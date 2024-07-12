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
import inetsoft.util.CoreTool;
import inetsoft.util.swap.*;

import java.nio.ByteBuffer;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XStringColumn, maintains the meta information and data of one string table
 * column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XStringColumn extends AbstractTableColumn {
   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XStringColumn(isize, size);
         }

         @Override
         public Class<String> getColType() {
            return String.class;
         }
      };
   }

   /**
    * Create an instance of <tt>XStringColumn</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XStringColumn(char isize, char size) {
      this(null, isize, size);
   }

   /**
    * Create an instance of <tt>XStringColumn</tt>.
    * @param creator the specified table column creator.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XStringColumn(XTableColumnCreator creator, char isize, char size) {
      super();

      this.creator = creator;
      this.size = size;
      this.pos = 0;
      this.arr = new String[isize];
      this.cache = creator == null || creator.isCache() ? new XObjectCache<>() : null;
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
      return XSchema.STRING;
   }

   /**
    * Check if data is stored as primitive value.
    * @return <tt>true</tt> if data is stored as primitive value,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimitive() {
      return false;
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
         String[] oarr = arr;
         String[] narr = new String[nsize];
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
      String s = CoreTool.getDataString(obj, false);
      arr[pos++] = cacheStringForSpeed(s);
      bytelen += XSwapUtil.length(arr[pos - 1]);
      return null;
   }

   /**
    * Cache string using instance cache, as it's faster than intern().
    */
   private String cacheStringForSpeed(String s) {
      if(s == null) {
         return null;
      }

      if(cache != null) {
         s = cache.get(s);

         if((cache.size() & 0xff) == 0xff && cache.getRatio() < 0.7) {
            cache = null;

            if(creator != null) {
               creator.setCache(false);
            }
         }
      }

      return s;
   }

   /**
    * Cache string using intern, as it's generally more memory efficient than using the instance
    * cache.
    */
   private String cacheStringForMemoryEfficiency(String s) {
      if(s == null) {
         return null;
      }

      return STRINGS.intern(s);
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public Object getObject(int r) {
      Object[] arr = access();

      try {
         return arr[r];
      }
      catch(ArrayIndexOutOfBoundsException ex) {
         LOG.error("Access index out of bounds: " + r + " of " + arr.length);
         throw ex;
      }
   }

   /**
    * Check if the value in one row is null.
    * @param r the specified row index.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r) {
      Object[] arr = access();
      return arr[r] == null;
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r) {
      return 0;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r) {
      return 0;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r) {
      return 0;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r) {
      return 0;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r) {
      return 0;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r) {
      return 0;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r) {
      return false;
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
    * Complete this table column.
    */
   @Override
   public void complete() {
      this.removeObjectPool();
   }

   @Override
   public void removeObjectPool() {
      cache = null;
   }

   /**
    * Dispose the serializable.
    */
   @Override
   public void dispose() {
      arr = null;
      removeObjectPool();
   }

   @Override
   public void copyFromBuffer(ByteBuffer buf) {
      bytelen = 0;
      String[] arr = new String[pos];

      for(int i = 0; i < pos; i++) {
         String val = XSwapUtil.readString(buf);
         arr[i] = cacheStringForMemoryEfficiency(val);
         bytelen += XSwapUtil.length(arr[i]);
      }

      this.arr = arr;
   }

   @Override
   public ByteBuffer copyToBuffer() {
      int i = 0;

      try {
         ByteBuffer buf = getByteBuffer((int) bytelen);

         for(i = 0; i < pos; i++) {
            XSwapUtil.writeString(buf, arr[i]);
         }

         removeObjectPool();
         XSwapUtil.flip(buf);
         return buf;
      }
      catch(Exception ex) {
         LOG.error("Failed to read swapped value(" + i + " of " + pos + "): " + ex.getMessage(), ex);
      }

      return null;
   }

   private ByteBuffer getByteBuffer(int len) {
      if(len <= BYTE_BUFFER_POOL_SIZE_LIMIT) {
         return ByteBufferPool.getByteBuffer(len);
      }
      else {
         return ByteBuffer.allocate(len);
      }
   }

   /**
    * Load the array. It should never be null.
    */
   private final Object[] access() {
      Object[] arr = this.arr;

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
      this.removeObjectPool();
   }

   @Override
   public int getInMemoryLength() {
      return access().length;
   }

   private String[] arr;
   private char size;
   private long bytelen;
   private XObjectCache<String> cache;
   private XTableColumnCreator creator;

   private static final Interner<String> STRINGS = Interners.newWeakInterner();
   private static final int BYTE_BUFFER_POOL_SIZE_LIMIT = 1000000; // 1MB
   private static final Logger LOG = LoggerFactory.getLogger(XStringColumn.class);
}
