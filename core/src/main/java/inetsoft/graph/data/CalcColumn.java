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
package inetsoft.graph.data;

import inetsoft.report.TableLens;

import java.io.Serializable;

/**
 * This interface defines the API for calculating the column values in a
 * dataset.
 *
 * @version 10.2, 8/15/2009
 * @author InetSoft Technology Corp
 */
public interface CalcColumn extends Cloneable, Serializable {
   /**
    * Calculate the value at the row.
    * @param row the row index of the dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   Object calculate(DataSet data, int row, boolean first, boolean last);

   /**
    * for crosstab calculator.
    * @return column index of the calculator base column in the tablelens.
    */
   default int getColIndex() {
      return -1;
   }

   /**
    * for crosstab calculator.
    * @param index column index of the calculator base column in the tablelens.
    */
   default void setColIndex(int index) {
   }

   /**
    * Calculate the value at the row.
    * @param table the base tablelens.
    * @param row the row index of the sorted dataset.
    * @param first true if this is the beginning of a series.
    * @param last true if this is the end of a series.
    */
   default Object calculate(TableLens table, int row, boolean first, boolean last) {
      return null;
   }

   /**
    * Get the column header.
    */
   String getHeader();

   /**
    * Get the column type.
    */
   Class getType();

   /**
    * Check if this column should be treated as a measure.
    */
   boolean isMeasure();

   /**
    * Get field.
    */
   String getField();

   /**
    * Method called at the end of (one run of) calculation.
    */
   default void complete() {
   }

   /**
    * Whether support the sort by value.
    * @return
    */
   default boolean supportSortByValue() {
      return false;
   }

   /**
    * This marks an invalid value. For example, in ChangeCalc from previous value, the
    * first value has no previous value to compare with, so the calculation result is
    * invalid.
    */
   Object INVALID = Double.valueOf(Double.MIN_VALUE);
}
