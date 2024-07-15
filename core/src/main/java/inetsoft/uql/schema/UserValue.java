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

/**
 * User value node.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class UserValue extends XValueNode {
   /**
    * Create a user value node.
    */
   public UserValue() {
   }

   /**
    * Create a user value node.
    */
   public UserValue(String name) {
      super(name);
   }

   /**
    * Create a user value node.
    */
   public UserValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.USER;
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) {
      setValue(str);
   }
}

