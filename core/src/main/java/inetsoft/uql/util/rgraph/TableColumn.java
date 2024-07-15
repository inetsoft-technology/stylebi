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
package inetsoft.uql.util.rgraph;


/**
 * A table and column.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableColumn {
   /**
    * Create a table column.
    */
   public TableColumn(TableNode table, String col) {
      this.table = table;
      this.col = col;
   }

   /**
    * Get the table this column belongs to.
    */
   public TableNode getTable() {
      return table;
   }

   /**
    * Get the column name.
    */
   public String getColumn() {
      return col;
   }

   /**
    * Two columns are equal if they belong to the same table and have 
    * same names.
    */
   public boolean equals(Object obj) {
      if(obj instanceof TableColumn) {
         TableColumn tc = (TableColumn) obj;

         return table.equals(tc.table) && col.equals(tc.col);
      }

      return false;
   }

   private TableNode table;
   private String col;
}

