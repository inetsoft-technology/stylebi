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

/**
 * Enum value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class EnumValue extends StringValue {
   /**
    * Create a Enum value node.
    */
   public EnumValue() {
   }

   /**
    * Create a Enum value node.
    */
   public EnumValue(String name) {
      super(name);
   }

   /**
    * Create a Enum value node.
    */
   public EnumValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.ENUM;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      setValue(str);
   }
}

