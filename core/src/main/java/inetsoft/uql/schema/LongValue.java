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
 * Long value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LongValue extends XValueNode {
   /**
    * Create a long value node.
    */
   public LongValue() {
   }

   /**
    * Create a long value node.
    */
   public LongValue(String name) {
      super(name);
   }

   /**
    * Create a long value node.
    */
   public LongValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.LONG;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      try {
         if(str.length() == 0) {
            setValue(Long.valueOf(0));
         }
         else if(format != null) {
            setValue(Long.valueOf(format.parse(str).longValue()));
         }
         else {
            setValue(Long.valueOf(str));
         }
      }
      catch(Exception ex) {
         setValue(Long.valueOf(str));
      }
   }

   /**
    * Get the value as a int.
    */
   public long longValue() {
      Object val = getValue();

      return (val != null) ? ((Number) val).longValue() : 0;
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
      return Long.valueOf(longValue());
   }

   NumberFormat format = null;
}

