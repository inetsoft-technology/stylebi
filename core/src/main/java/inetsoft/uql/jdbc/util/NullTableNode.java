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
package inetsoft.uql.jdbc.util;

import inetsoft.uql.XMetaInfo;
import inetsoft.uql.XTableNode;

/**
 * Null table node contains nothing.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class NullTableNode extends XTableNode {
   /**
    * Constructor.
    */
   public NullTableNode() {
      super();
   }

   /**
    * Check if there are more rows. The first time this method is called,
    * the cursor is positioned at the first row of the table.
    * @return true if there are more rows.
    */
   @Override
   public boolean next() {
      return false;
   }

   /**
    * Get the number of columns in the table.
    */
   @Override
   public int getColCount() {
      return 0;
   }

   /**
    * Get the column name.
    * @param col column index.
    * @return column name or alias if defined.
    */
   @Override
   public String getName(int col) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the column type.
    * @param col column index.
    * @return column data type.
    */
   @Override
   public Class getType(int col) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the value in the current row at the specified column.
    * @param col column index.
    * @return column value.
    */
   @Override
   public Object getObject(int col) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Get the meta info at the specified column.
    * @param col column index.
    * @return the meta info.
    */
   @Override
   public XMetaInfo getXMetaInfo(int col) {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Move the cursor to the beginning. This is ignored if the cursor
    * is already at the beginning.
    * @return true if the rewinding is successful.
    */
   @Override
   public boolean rewind() {
      return true;
   }

   /**
    * Check if the cursor can be rewinded.
    * @return true if the cursor can be rewinded.
    */
   @Override
   public boolean isRewindable() {
      return true;
   }
}