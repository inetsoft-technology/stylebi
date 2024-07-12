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

import inetsoft.uql.XNode;

/**
 * Long type node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class LongType extends NumberBaseType {
   /**
    * Create a long type node.
    */
   public LongType() {
   }

   /**
    * Create a long type node.
    */
   public LongType(String name) {
      super(name);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.LONG;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      LongValue node = new LongValue();

      node.setName(getName());
      applyFormat(node);

      return node;
   }
}

