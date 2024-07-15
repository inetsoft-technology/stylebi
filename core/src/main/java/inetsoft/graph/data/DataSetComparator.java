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

import java.io.Serializable;
import java.util.Comparator;

/**
 * This interface defines the api for a comparator that is tied to a specific
 * dataset (e.g. sorting by the value of a different column).
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface DataSetComparator extends Comparator, Serializable {
   /**
    * Get a comparator for the dataset. If this comparator is not initialized or
    * is for this dataset, reuse self. Otherwise create a new comparator for the
    * new dataset.
    */
   public DataSetComparator getComparator(DataSet data);

   /**
    * Get the comparator for a sub-range of the dataset where the row is in.
    * If there is no separate comparator, just return self.
    */
   public Comparator getComparator(int row);

   /**
    * Compare the two rows. This method is called when the comparison should
    * use the row value instead of aggregated value.
    */
   public int compare(DataSet data, int row1, int row2);

   default String getValueCol() {
      return null;
   }

   /**
    * Get a dataset specific comparator if the orginal comparator is a DataSetComparator.
    */
   public static Comparator getComparator(Comparator comp, DataSet data) {
      if(comp instanceof DataSetComparator) {
         return ((DataSetComparator) comp).getComparator(data);
      }

      return comp;
   }
}
