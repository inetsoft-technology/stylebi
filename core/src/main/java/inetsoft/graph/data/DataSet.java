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

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

/**
 * DataSet, as the data provider, provides data for a graph.
 *
 * @version 10, 3/7/2008
 * @author InetSoft Technology Corp
 */
public interface DataSet extends Cloneable, Serializable {
   /**
    * Return the data at the specified cell.
    * @param col the specified column name.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   Object getData(String col, int row);

   /**
    * Return the data at the specified cell.
    * @param col the specified column index.
    * @param row the specified row index.
    * @return the data at the specified cell.
    */
   Object getData(int col, int row);

   /**
    * Return the column header at the specified column.
    * @param col the specified column index.
    * @return the column header at the specified column.
    */
   String getHeader(int col);

   /**
    * Get the data type of the column.
    */
   Class<?> getType(String col);

   /**
    * Get the index of the specified header.
    * @param col the specified column header.
    */
   int indexOfHeader(String col);

   /**
    * Get the index of the specified header
    * @param all true to include calc column even if it hasn't been generated.
    */
   default int indexOfHeader(String col, boolean all) {
      return indexOfHeader(col);
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   Comparator<?> getComparator(String col);

   /**
    * Get the comparator without wrapping the original comparator set on the dataset.
    */
   default Comparator getOrigComparator(String col) {
      return getComparator(col);
   }

   /**
    * Check if the column is measure.
    * The designation of measure may impact the default scale created by
    * plotter.
    * @param col the specified column name.
    * @return <tt>true</true> if is measure, <tt>false</tt> otherwise.
    */
   boolean isMeasure(String col);

   /**
    * Return the number of rows in the data set.
    */
   int getRowCount();

   /**
    * Return the number of columns in the data set.
    */
   int getColCount();

   /**
    * This method must be called before the calculated columns can be used.
    * @param dim the innermost dimension column in the graph.
    * @param rows a list of row indexes to calculate values using CalcColumn.
    * @param calcMeasures false if measure calc columns should be ignored (for optimization).
    */
   void prepareCalc(String dim, int[] rows, boolean calcMeasures);

   /**
    * Initialize any data for this graph.
    * @param graph the (innermost) egraph that will plot this dataset.
    * @param coord the (innermost) coordinate that will plot this dataset.
    * @param dataset
    */
   void prepareGraph(EGraph graph, Coordinate coord, DataSet dataset);

   /**
    * Add a calculated columns.
    */
   void addCalcColumn(CalcColumn col);

   /**
    * Get the calculated columns.
    */
   List<CalcColumn> getCalcColumns();

   /**
    * Remove all calculated columns.
    */
   void removeCalcColumns();

   /**
    * Add a calculated rows.
    */
   void addCalcRow(CalcRow col);

   /**
    * Get the calculated rows.
    */
   List<CalcRow> getCalcRows();

   /**
    * Remove all calculated rows.
    */
   void removeCalcRows();

   /**
    * Remove calc rows with the specified type.
    */
   void removeCalcRows(Class<?> cls);

   /**
    * Clear the calculated column and row values.
    */
   void removeCalcValues();

   /**
    * Clear the calculated column values.
    */
   void removeCalcColValues();

   /**
    * Clear the calculated row values.
    */
   void removeCalcRowValues();

   /**
    * @return a copy of this object
    */
   Object clone();

   /**
    * Make a copy of this dataset.
    * @param shallow true to only copy the attributes (exclude base dataset).
    */
   DataSet clone(boolean shallow);

   /**
    * Check if measure columns contains valid values.
    */
   default boolean containsValue(String ...measures) {
      return true;
   }

   /**
    * Interrupt any processing and release resources in dataset. The base data is not
    * destroyed if it's based on XTable.
    */
   default void dispose() {
   }

   default boolean isDisposed() {
      return false;
   }
}
