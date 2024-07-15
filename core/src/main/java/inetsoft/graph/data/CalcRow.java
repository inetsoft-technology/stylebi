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
package inetsoft.graph.data;

import inetsoft.report.TableLens;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * This interface defines the API for calculating the row values in a
 * dataset.
 *
 * @version 10.2, 8/15/2009
 * @author InetSoft Technology Corp
 */
public interface CalcRow extends Cloneable, Serializable {
   /**
    * Calculate the values for the dataset.
    * @return the rows to append to the dataset.
    */
   List<Object[]> calculate(DataSet data);

   /**
    * @param table the base table to apply calculator.
    */
   default List<Object[]> calculate(TableLens table) {
      return null;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   Comparator getComparator(String col);

   /**
    * Return true if this row calc should be processed before CalcColumn, so the added
    * rows are available for CalcColumn.
    */
   default boolean isPreColumn() {
      return false;
   }

   /**
    * Check if the calculated value is always no null.
    */
   default boolean isNoNull() {
      return true;
   }
}
