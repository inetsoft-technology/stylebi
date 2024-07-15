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
package inetsoft.mv;

import inetsoft.uql.table.XSwappableTable;

import java.util.stream.IntStream;

/**
 * Interface for table classes that wrap a data frame.
 */
public interface DFWrapper {
   /**
    * Get the spark DataFrame for this table filter.
    */
   Object getDF();

   /**
    * Get a RDD to perform the process. This will be called if the
    * dataframe fails (most likely due to column type mismatch).
    */
   Object getRDD();

   /**
    * If this is a delegate class, return the base DFWrapper.
    */
   DFWrapper getBaseDFWrapper();

   /**
    * Get the column headers.
    */
   String[] getHeaders();

   /**
    * Get the column type.
    */
   Class getColType(int col);

   /**
    * Get optional column identifier.
    */
   String getColumnIdentifier(int col);

   /**
    * Set the meta infos on the table lens from MV.
    */
   void setXMetaInfos(XSwappableTable lens);

   /**
    * This method is called when a DF has completed loading or it's
    * disposed without loading data.
    */
   void completed();

   /**
    * Returns an id that uniquely identifies this data frame (its contents).
    */
   long dataId();

   /**
    * Return sub dfs such as the tables used in a join.
    */
   default DFWrapper[] subDFs() {
      return new DFWrapper[0];
   }

   default String[] getColumnIdentifiers() {
      return IntStream.range(0, getHeaders().length)
         .mapToObj(this::getColumnIdentifier).toArray(String[]::new);
   }
}
