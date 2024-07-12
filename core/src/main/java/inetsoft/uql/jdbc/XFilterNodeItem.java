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
package inetsoft.uql.jdbc;

import inetsoft.uql.HierarchyItem;

import java.util.Objects;

/**
 * represents a XFilterNode and Level.
 */
public class XFilterNodeItem implements HierarchyItem {
   public XFilterNodeItem(XFilterNode node, int level) {
      this.node = node;
      this.level = level;
   }

   @Override
   public int getLevel() {
      return level;
   }

   @Override
   public void setLevel(int level) {
      this.level = level;
   }

   public XFilterNode getNode() {
      return node;
   }

   @Override
   public XFilterNodeItem clone() {
      return new XFilterNodeItem((XFilterNode) getNode().clone(), getLevel());
   }

   public String toString() {
      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < level; i++) {
         buf.append(".........");
      }

      buf.append("        ");
      buf.append(node.toString());

      return buf.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(level, node);
   }

   public int level;
   public XFilterNode node;
}

