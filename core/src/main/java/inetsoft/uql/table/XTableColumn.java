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

import java.io.File;

/**
 * XTableColumn, maintains the meta information and data of one table column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public interface XTableColumn extends XSerializable {
   /**
    * Get the data type.
    * @return the data type. Refer to <tt>XSchema</tt> for detail.
    */
   public String getType();

   /**
    * Check if data is stored as primitive value.
    */
   public boolean isPrimitive();

   /**
    * Get the capacity of this table column.
    */
   public int capacity();

   /**
    * Add an object.
    * @param obj the specified object.
    * @return the preferred table column.
    */
   public XTableColumn addObject(Object obj);

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   public Object getObject(int r);

   /**
    * Get the double value in one row.
    */
   public double getDouble(int r);

   /**
    * Get the float value in one row.
    */
   public float getFloat(int r);

   /**
    * Get the long value in one row.
    */
   public long getLong(int r);

   /**
    * Get the int value in one row.
    */
   public int getInt(int r);

   /**
    * Get the short value in one row.
    */
   public short getShort(int r);

   /**
    * Get the byte value in one row.
    */
   public byte getByte(int r);

   /**
    * Get the boolean value in one row.
    */
   public boolean getBoolean(int r);

   /**
    * Check if the value in one row is null.
    */
   public boolean isNull(int r);

   /**
    * Complete this table column.
    */
   public void complete();

   /**
    * Get the table column creator.
    */
   public XTableColumnCreator getCreator0();

   /**
    * Get the length (number of items) in this column.
    */
   public int length();

   /**
    * Set the swap file information.
    */
   public void setSwapInfo(File file, long pos, int size, int len);

   /**
    * Clears internal object pool.
    */
   public void removeObjectPool();

   /**
    * Get the current in-memory length.
    */
   default int getInMemoryLength() {
      return 0;
   }

   /**
    * Get the swap file information.
    */
   default String getSwapLog() {
      return "";
   }

   /**
    * Set an explicit swap info.
    */
   default void setSwapLog(String info) {
   }
}
