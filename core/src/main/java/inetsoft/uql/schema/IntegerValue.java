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
package inetsoft.uql.schema;

import java.text.Format;
import java.text.NumberFormat;

/**
 * Integer value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class IntegerValue extends XValueNode {
   /**
    * Create an integer value node.
    */
   public IntegerValue() {
   }

   /**
    * Create an integer value node.
    */
   public IntegerValue(String name) {
      super(name);
   }

   /**
    * Create an integer value node.
    */
   public IntegerValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.INTEGER;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      try {
         if(str.length() == 0) {
            setValue(Integer.valueOf(0));
         }
         else if(format != null) {
            setValue(Integer.valueOf(format.parse(str).intValue()));
         }
         else {
            setValue(Integer.valueOf(str));
         }
      }
      catch(Exception ex) {
         setValue(Integer.valueOf(str));
      }
   }

   /**
    * Get the value as a int.
    */
   public int intValue() {
      Object val = getValue();

      return (val != null) ? ((Number) val).intValue() : 0;
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
      return Integer.valueOf(intValue());
   }

   NumberFormat format = null;
}

