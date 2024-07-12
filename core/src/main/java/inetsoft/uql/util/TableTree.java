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
package inetsoft.uql.util;

import inetsoft.uql.*;

/**
 * Simulate a tree with a table.
 */
public class TableTree extends XSequenceNode {
   public TableTree(XTableNode table) {
      super(table.getName());
      this.table = table;
   }

   // initialize the children when child count is called.
   @Override
   public int getChildCount() {
      if(first) {
         first = false;
         table.rewind();

         while(table.next()) {
            XNode child = new XNode(getName());

            for(int i = 0; i < table.getColCount(); i++) {
               String name = table.getName(i);
               Object val = table.getObject(i);
               int idx = name.indexOf('@');

               // @by danielz, "@" means the field is an attribute.
               if(idx >= 0) {
                  child.setAttribute(name.substring(idx + 1), val);
               }

               XNode field = new XNode(table.getName(i));
               field.setValue(val);
               child.addChild(field, false, false);
            }

            addChild(child, false, false);
         }
      }

      return super.getChildCount();
   }

   public XTableNode getTable() {
      return table;
   }

   /**
    * Check if is cacheable.
    */
   public boolean isCacheable() {
      return table == null ? true : table.isCacheable();
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      TableTree tree = (TableTree) super.clone();

      if(table != null) {
         tree.table = (XTableNode) table.clone();
      }

      return tree;
   }

   private XTableNode table;
   private boolean first = true;
}
