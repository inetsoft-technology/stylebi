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
package inetsoft.uql.schema;

import java.text.Format;
import java.text.NumberFormat;

/**
 * Float value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class FloatValue extends XValueNode {
   /**
    * Create a float value node.
    */
   public FloatValue() {
   }

   /**
    * Create a float value node.
    */
   public FloatValue(String name) {
      super(name);
   }

   /**
    * Create a float value node.
    */
   public FloatValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.FLOAT;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      try {
         if(str.length() == 0) {
            setValue(Float.valueOf(0));
         }
         else if(format != null) {
            setValue(Float.valueOf(format.parse(str).floatValue()));
         }
         else {
            setValue(Float.valueOf(str));
         }
      }
      catch(Exception ex) {
         setValue(Float.valueOf(str));
      }
   }

   /**
    * Get the value as a float.
    */
   public float floatValue() {
      Object val = getValue();

      return (val != null) ? ((Number) val).floatValue() : 0.0f;
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
      return Float.valueOf(floatValue());
   }

   NumberFormat format = null;
}

