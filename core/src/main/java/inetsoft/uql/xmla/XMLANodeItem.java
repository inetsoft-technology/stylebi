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
package inetsoft.uql.xmla;

import inetsoft.uql.HierarchyItem;

import java.util.Objects;

/**
 * Represents an XMLANode and Level.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class XMLANodeItem implements HierarchyItem {
   public XMLANodeItem(XMLANode node, int level) {
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

   public XMLANode getNode() {
      return node;
   }

   @Override
   public Object clone() {
      return new XMLANodeItem((XMLANode) getNode().clone(), getLevel());
   }

   @Override
   public int hashCode() {
      return Objects.hash(level, node);
   }

   private int level;
   private final XMLANode node;
}
