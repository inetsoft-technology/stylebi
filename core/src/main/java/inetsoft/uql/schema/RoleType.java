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
 * Role type node.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class RoleType extends XTypeNode {
   /**
    * Role type name.
    */
   public static final String TYPE_NAME = "$(role)";

   /**
    * Create a role type node.
    */
   public RoleType() {
      super(TYPE_NAME);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.ROLE;
   }

   /**
    * Return true if this is a primitive type.
    */
   @Override
   public boolean isPrimitive() {
      return true;
   }

   /**
    * Create a value tree corresponding to the data type defined
    * by this type.
    */
   @Override
   public XNode newInstance() {
      XNode node = new RoleValue();
      node.setName(getName());
      return node;
   }
}

