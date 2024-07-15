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

/**
 * DataSetFilter defines the common functions for a data set filter.
 * A dataset filter is a wrapper around another dataset that can add additional
 * calculation or filtering.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface DataSetFilter extends DataSet {
   /**
    * Get the base data set.
    */
   DataSet getDataSet();

   /**
    * Get the root data set.
    */
   DataSet getRootDataSet();

   /**
    * Get the base row index on its base data set of the specified row.
    * @param r the specified row index.
    * @return the base row index on base data set, -1 if no base row.
    */
   int getBaseRow(int r);

   /**
    * Get the root row index on its root data set of the specified row.
    * @param r the specified row index.
    * @return the root row index on root data set, -1 if no root row.
    */
   int getRootRow(int r);

   /**
    * Get the base column index on its base data set of the specified column.
    * @param c the specified column index.
    * @return the base column index on base data set, -1 if no base column.
    */
   int getBaseCol(int c);

   /**
    * Get the root column index on its root data set of the specified column.
    * @param c the specified column index.
    * @return the root column index on root data set, -1 if no root column.
    */
   int getRootCol(int c);

   default void dispose() {
      if(getDataSet() != null) {
         getDataSet().dispose();
      }
   }

   @Override
   default boolean isDisposed() {
      if(getDataSet() != null) {
         return getDataSet().isDisposed();
      }

      return false;
   }
}
