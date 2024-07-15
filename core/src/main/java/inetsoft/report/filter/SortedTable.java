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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.report.TableLens;

/**
 * This interface is implemented by all sorted tables to provide information
 * on the sorting columns.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface SortedTable extends TableLens {
   /**
    * Get the columns that the table is sorted on.
    * @return sort columns.
    */
   public int[] getSortCols();

   /**
    * Get the sorting order of the sorting columns.
    */
   public boolean[] getOrders();

   /**
    * Set the comparer for a sorting column.
    * @param col table column index.
    * @param comp comparer.
    */
   public void setComparer(int col, Comparer comp);

   /**
    * Get the comparer for a sorting column.
    * @param col the specified table column index.
    */
   public Comparer getComparer(int col);
}

