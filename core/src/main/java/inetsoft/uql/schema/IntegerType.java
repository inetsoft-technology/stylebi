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

import inetsoft.uql.XNode;

/**
 * Integer type node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class IntegerType extends NumberBaseType {
   /**
    * Create an integer type node.
    */
   public IntegerType() {
   }

   /**
    * Create an integer type node.
    */
   public IntegerType(String name) {
      super(name);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.INTEGER;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      IntegerValue node = new IntegerValue();

      node.setName(getName());
      applyFormat(node);

      return node;
   }
}

