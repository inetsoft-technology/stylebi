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
package inetsoft.uql.schema;

import java.text.Format;
import java.text.NumberFormat;

/**
 * Byte value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ByteValue extends XValueNode {
   /**
    * Create a byte value node.
    */
   public ByteValue() {
   }

   /**
    * Create a byte value node.
    */
   public ByteValue(String name) {
      super(name);
   }

   /**
    * Create a byte value node.
    */
   public ByteValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.BYTE;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      try {
         if(str.length() == 0) {
            setValue(Byte.valueOf((byte) 0));
         }
         else if(format != null) {
            setValue(Byte.valueOf(format.parse(str).byteValue()));
         }
         else {
            setValue(Byte.valueOf(str));
         }
      }
      catch(Exception ex) {
         setValue(Byte.valueOf(str));
      }
   }

   /**
    * Get the value as a byte.
    */
   public byte byteValue() {
      Object val = getValue();

      return (val != null) ? ((Number) val).byteValue() : (byte) 0;
   }

   /**
    * Set the format for formatting and parsing string values.
    */
   @Override
   public void setFormat(Format fmt) {
      this.format = (NumberFormat) fmt;
   }

   /**
    * from java Object to SQL type
    */
   @Override
   public Object toSQLValue() {
      return Byte.valueOf(byteValue());
   }

   NumberFormat format = null;
}

