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
package inetsoft.report;

/**
 * This defines the API for all table filters.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface TableFilter extends TableLens {
   /**
    * Get the original table of this filter.
    */
   TableLens getTable();

   /**
    * Set the base table of this filter.
    */
   void setTable(TableLens table);

   /**
    * Invalidate the table filter. When the filter is used, the underlying data
    * will be filtered again. This function is called if the base data has
    * changed.
    */
   void invalidate();

   /**
    * Get the base table row index corresponding to the filtered table.
    * If the row does not exist in the base table, it returns -1.
    * @param row row index in the filtered table.
    * @return corresponding row index in the base table.
    */
   int getBaseRowIndex(int row);

   /**
    * Get the base table column index corresponding to the filtered table.
    * If the column does not exist in the base table, it returns -1.
    * @param col column index in  the filtered table.
    * @return corresponding column index in the bast table.
    */
   int getBaseColIndex(int col);

   default TableLens[] getTables() {
      return new TableLens[] { getTable() };
   }
}
