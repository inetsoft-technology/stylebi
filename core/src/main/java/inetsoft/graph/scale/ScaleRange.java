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
package inetsoft.graph.scale;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;

import java.io.Serializable;
import java.util.Collection;

/**
 * This defines the interface for calculating the max and min of a numeric
 * scale.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface ScaleRange extends Cloneable, Serializable {
   /**
    * Get the value.
    */
   public double getValue(Object data);

   /**
    * Set whether to force all values to be converted to absolute value.
    */
   public void setAbsoluteValue(boolean abs);

   /**
    * Check whether to force all values to be converted to absolute value.
    */
   public boolean isAbsoluteValue();

   /**
    * Calculate the range of values of the specified columns.
    *
    * @param dataset  the dataset.
    * @param cols     the numeric columns to find range values.
    * @param selector select which rows are included in calculation.
    *
    * @return an array of two values, minimum and maximum of the range.
    */
   public double[] calculate(DataSet dataset, String[] cols, GraphtDataSelector selector);

   /**
    * Set the rows that the specified measure is used for plotting. If a range is already
    * set for the measure, the new range is merged into the existing range.
    */
   public void setMeasureRange(String measure, int start, int end);

   /**
    * Get the range of ranges defined for the measure.
    * @return the range as min and max at index 0 and 1. null if range is not defined.
    */
   public int[] getMeasureRange(String measure);

   /**
    * Get all the measures with row range defined.
    */
   public Collection<String> getRangeMeasures();

   /**
    * Get the starting row index for the measures.
    */
   default int getStartRow(DataSet data, String... measures) {
      return 0;
   }

   /**
    * Get the ending row index for the measures.
    */
   default int getEndRow(DataSet data, String... measures) {
      return data.getRowCount();
   }

   /**
    * Copy measure row ranges into this scale range, if the range is not defined in this.
    */
   default void copyMeasureRanges(ScaleRange range) {
      Collection<String> measures = getRangeMeasures();

      for(String measure : range.getRangeMeasures()) {
         if(!measures.contains(measure)) {
            setMeasureRange(measure, range.getMeasureRange(measure)[0], range.getMeasureRange(measure)[1]);
         }
      }
   }
}