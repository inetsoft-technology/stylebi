/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.jdbc;


/**
 * represents a XSet and Level.
 */
public class XSetItem extends XFilterNodeItem {
   public XSetItem(XSet set, int level) {
      super(set, level);
   }

   public XSet getXSet() {
      return (XSet) node;
   }

   @Override
   public XSetItem clone() {
      return new XSetItem((XSet) getXSet().clone(), getLevel());
   }

   public String toString() {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < level; i++) {
         buf.append(".........");
      }

      if(node.isIsNot()) {
         buf.append("not  ");
      }
      else {
         buf.append("        ");
      }

      buf.append(((XSet) node).getRelation());

      return buf.toString();
   }
}

