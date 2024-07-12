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

import javax.xml.bind.DatatypeConverter;

/**
 * Boolean value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class BooleanValue extends XValueNode {
   /**
    * Create a boolean value node.
    */
   public BooleanValue() {
   }

   /**
    * Create a boolean value node.
    */
   public BooleanValue(String name) {
      super(name);
   }

   /**
    * Create a boolean value node.
    */
   public BooleanValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.BOOLEAN;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      final boolean value;

      if(str == null || str.length() == 0) {
         value = false;
      }
      else {
         value = DatatypeConverter.parseBoolean(str);
      }

      setValue(value);
   }

   /**
    * Get the value as a boolean.
    */
   public boolean booleanValue() {
      Object val = getValue();

      return val != null && (Boolean) val;
   }

   /**
    * from java Object to SQL type
    */
   public Object toSQLType() {
      return booleanValue();
   }
}

