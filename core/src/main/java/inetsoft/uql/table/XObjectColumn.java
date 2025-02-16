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
import inetsoft.util.graphics.ImageWrapper;
import inetsoft.util.swap.*;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XObjectColumn, maintains the meta information and data of one object column.
 * The capacity of a table column should not exceed <tt>65534</tt>, which is
 * <tt>CHARACTER.MAX_VALUE - 1</tt>.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XObjectColumn extends AbstractTableColumn {
   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XObjectColumn(this, isize, size);
         }

         @Override
         public Class getColType() {
            return null;
         }
      };
   }

   /**
    * Create an instance of <tt>XObjectColumn</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XObjectColumn(char isize, char size) {
      this(null, isize, size);
   }

   /**
    * Create an instance of <tt>XObjectColumn</tt>.
    * @param creator the specified table column creator.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XObjectColumn(XTableColumnCreator creator, char isize, char size) {
      super();

      this.creator = creator;
      this.isize = isize;
      this.size = size;
      this.pos = 0;
      this.arr = new Object[isize];
      this.serial = UNKNOWN;
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
    * @return the data type. Refer to <tt>XSchema</tt> for detail.
    */
   @Override
   public String getType() {
      return XSchema.UNKNOWN;
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
         Object[] oarr = arr;
         Object[] narr = new Object[nsize];
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
      obj = tryCache(obj);
      arr[pos++] = obj;

      if(serial == UNKNOWN && obj != null) {
         image = obj instanceof Image;

         serial = (image || obj instanceof Serializable ||
            obj instanceof Externalizable) ?
               TRUE : FALSE;

         if(!image && (creator == null || creator.isDynamic())) {
            XTableColumn column = getPreferredColumn(obj.getClass());

            if(column != null) {
               for(int i = 0; i < pos; i++) {
                  column.addObject(arr[i]);
               }

               return column;
            }
         }
      }

      return null;
   }

   private Object tryCache(Object obj) {
      if(cache != null && obj != null) {
         obj = cache.get(obj);

         if((cache.size() & 0xff) == 0xff && cache.getRatio() < 0.7) {
            cache = null;

            if(creator != null) {
               creator.setCache(false);
            }
         }
      }

      return obj;
   }

   /**
    * Get the preferred column.
    * @return the preferred column if any, <tt>null</tt> otherwise.
    */
   private XTableColumn getPreferredColumn(Class cls) {
      if(String.class.isAssignableFrom(cls)) {
         return new XStringColumn(isize, size);
      }
      else if(Boolean.class.isAssignableFrom(cls)) {
         return new XBooleanColumn(isize, size);
      }
      else if(Float.class.isAssignableFrom(cls)) {
         return new XFloatColumn(isize, size);
      }
      else if(Double.class.isAssignableFrom(cls)) {
         return new XDoubleColumn(isize, size);
      }
      else if(Byte.class.isAssignableFrom(cls)) {
         return new XShortColumn(isize, size);
      }
      else if(Short.class.isAssignableFrom(cls)) {
         return new XShortColumn(isize, size);
      }
      else if(Integer.class.isAssignableFrom(cls)) {
         return new XIntegerColumn(isize, size);
      }
      else if(Long.class.isAssignableFrom(cls)) {
         return new XLongColumn(isize, size);
      }
      else if(java.sql.Date.class.isAssignableFrom(cls)) {
         return new XDateColumn(isize, size);
      }
      else if(java.sql.Time.class.isAssignableFrom(cls)) {
         return new XTimeColumn(isize, size);
      }
      else if(java.sql.Timestamp.class.isAssignableFrom(cls)) {
         return new XTimestampColumn(isize, size);
      }
      else if(java.util.Date.class.isAssignableFrom(cls)) {
         return new XTimestampColumn(isize, size);
      }
      else if(java.math.BigDecimal.class.isAssignableFrom(cls)) {
         return XBDDoubleColumn.isRecommended() ?
            new XBDDoubleColumn(isize, size) : null;
      }
      else if(java.math.BigInteger.class.isAssignableFrom(cls)) {
         return new XBILongColumn(isize, size);
      }

      return null;
   }

   /**
    * Get the preferred column.
    * @return the preferred column if any, <tt>null</tt> otherwise.
    */
   public static XTableColumnCreator getCreator(Class<?> cls) {
      if(cls == null) {
         return getCreator();
      }
      else if(String.class.isAssignableFrom(cls)) {
         return XStringColumn.getCreator();
      }
      else if(Boolean.class.isAssignableFrom(cls)) {
         return XBooleanColumn.getCreator();
      }
      else if(Float.class.isAssignableFrom(cls)) {
         return XFloatColumn.getCreator();
      }
      else if(Double.class.isAssignableFrom(cls)) {
         return XDoubleColumn.getCreator();
      }
      else if(Byte.class.isAssignableFrom(cls)) {
         return XByteColumn.getCreator();
      }
      else if(Short.class.isAssignableFrom(cls)) {
         return XShortColumn.getCreator();
      }
      else if(Integer.class.isAssignableFrom(cls)) {
         return XIntegerColumn.getCreator();
      }
      else if(Long.class.isAssignableFrom(cls)) {
         return XLongColumn.getCreator();
      }
      else if(java.sql.Date.class.isAssignableFrom(cls)) {
         return XDateColumn.getCreator();
      }
      else if(java.sql.Time.class.isAssignableFrom(cls)) {
         return XTimeColumn.getCreator();
      }
      else if(java.sql.Timestamp.class.isAssignableFrom(cls)) {
         return XTimestampColumn.getCreator();
      }
      else if(java.util.Date.class.isAssignableFrom(cls)) {
         return XTimestampColumn.getCreator();
      }
      else if(java.math.BigDecimal.class.isAssignableFrom(cls)) {
         return XBDDoubleColumn.isRecommended() ?
            XBDDoubleColumn.getCreator() : XObjectColumn.getCreator();
      }
      else if(java.math.BigInteger.class.isAssignableFrom(cls)) {
         return XBILongColumn.getCreator();
      }

      return getCreator();
   }

   /**
    * Get the preferred column.
    * @return the preferred column if any, <tt>null</tt> otherwise.
    */
   public static XTableColumnCreator getCreator(String cls) {
      if(cls == null) {
         return getCreator();
      }
      else if(XSchema.STRING.equals(cls)) {
         return XStringColumn.getCreator();
      }
      else if(XSchema.BOOLEAN.equals(cls)) {
         return XBooleanColumn.getCreator();
      }
      else if(XSchema.FLOAT.equals(cls)) {
         return XFloatColumn.getCreator();
      }
      else if(XSchema.DOUBLE.equals(cls)) {
         return XDoubleColumn.getCreator();
      }
      else if(XSchema.DECIMAL.equals(cls)) {
         return XDoubleColumn.getCreator();
      }
      else if(XSchema.BYTE.equals(cls)) {
         return XByteColumn.getCreator();
      }
      else if(XSchema.SHORT.equals(cls)) {
         return XShortColumn.getCreator();
      }
      else if(XSchema.INTEGER.equals(cls)) {
         return XIntegerColumn.getCreator();
      }
      else if(XSchema.LONG.equals(cls)) {
         return XLongColumn.getCreator();
      }
      else if(XSchema.DATE.equals(cls)) {
         return XDateColumn.getCreator();
      }
      else if(XSchema.TIME.equals(cls)) {
         return XTimeColumn.getCreator();
      }
      else if(XSchema.TIME_INSTANT.equals(cls)) {
         return XTimestampColumn.getCreator();
      }

      return getCreator();
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public Object getObject(int r) {
      Object[] arr = access();
      return arr[r];
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
    * Check if the table column is in valid state.
    */
   @Override
   public boolean isValid() {
      return arr != null;
   }

   /**
    * Check if is serializable.
    */
   @Override
   public boolean isSerializable() {
      return serial != FALSE;
   }

   /**
    * Dispose the serializable.
    */
   @Override
   public void dispose() {
      arr = null;
      removeObjectPool();
   }

   /**
    * Complete this table column.
    */
   @Override
   public void complete() {
      removeObjectPool();
   }

   @Override
   public void removeObjectPool() {
      cache = null;
   }

   @Override
   public void copyFromBuffer(ByteBuffer buf) {
      copyFromBuffer0(buf);
   }

   public void copyFromBuffer0(ByteBuffer buf) {
      if(serial == FALSE) {
         return;
      }

      if(creator.isCache()) {
         cache = new XObjectCache<>();
      }

      Kryo kryo = XSwapUtil.getKryo();

      try {
         ByteArrayInputStream in = new ByteArrayInputStream(buf.array());
         Input oin = new Input(in);

         Object[] arr = new Object[pos];

         for(int i = 0; i < pos; i++) {
            Object obj = kryo.readClassAndObject(oin);

            if(image) {
               obj = ((ImageWrapper) obj).unwrap();
            }

            obj = tryCache(obj);
            arr[i] = obj;
         }

         cache = null;
         this.arr = arr;
      }
      catch(Exception ex) {
         LOG.error("Failed to read data", ex);
      }
   }

   @Override
   public ByteBuffer copyToBuffer() {
      if(serial == FALSE || pos == 0) {
         return null;
      }

      final Kryo kryo = XSwapUtil.getKryo();

      try {
         ByteArrayOutputStream2 bout = new ByteArrayOutputStream2();
         Output oout = new Output(bout);

         for(int i = 0; i < pos; i++) {
            if(image) {
               kryo.writeClassAndObject(oout, new ImageWrapper((Image) arr[i]));
            }
            else {
               kryo.writeClassAndObject(oout, arr[i]);
            }
         }

         oout.close();
         return ByteBuffer.wrap(bout.toByteArray());
      }
      catch(Exception ex) {
         LOG.error("Failed to write data", ex);
      }

      return null;
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
      removeObjectPool();
   }

   @Override
   public int getInMemoryLength() {
      return access().length;
   }

   protected int getHeaderLength() {
      return HEADER_LENGTH;
   }

   public boolean isImage() {
      return image;
   }

   protected Object getCache(Object o) {
      return cache != null && o != null ? cache.get(o) : null;
   }

   protected boolean hasCache() {
      return cache != null;
   }

   private static final int HEADER_LENGTH = 8;

   private Object[] arr; // object array
   private char isize; // initial size
   private char size; // max size
   private byte serial; // serializable flag
   private boolean image; // image flag
   private XObjectCache<Object> cache; // object cache
   private XTableColumnCreator creator; // column creator
   private static final Logger LOG = LoggerFactory.getLogger(XObjectColumn.class);
}
