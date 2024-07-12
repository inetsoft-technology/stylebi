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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.visual.ElementVO;

import java.util.*;

/**
 * AbstractScaleRange implements the common scale range interfaces.
 */
public abstract class AbstractScaleRange implements ScaleRange {
   /**
    * Get a numeric value. If the value is not numeric, return 0.
    */
   @Override
   @TernMethod
   public double getValue(Object data) {
      if(!(data instanceof Number)) {
         return 0;
      }

      double val = ((Number) data).doubleValue();
      return abs ? Math.abs(val) : val;
   }

   /**
    * Set whether to force all values to be absolute.
    */
   @Override
   @TernMethod
   public void setAbsoluteValue(boolean abs) {
      this.abs = abs;
   }

   /**
    * Check whether to force all values to be absolute.
    */
   @Override
   @TernMethod
   public boolean isAbsoluteValue() {
      return abs;
   }

   @Override
   @TernMethod
   public void setMeasureRange(String measure, int start, int end) {
      measureRange.put(measure, new int[] { start, end });
   }

   @Override
   @TernMethod
   public int[] getMeasureRange(String measure) {
      return measureRange.get(measure);
   }

   @Override
   @TernMethod
   public Collection<String> getRangeMeasures() {
      return measureRange.keySet();
   }

   @Override
   public int getStartRow(DataSet data, String... measures) {
      int startRow = AbstractScaleRange.this.getStartRow(measures);
      // for facet, need to find the actual row in data (SortedDataSet -> SubDataSet)
      // from the base row index. (59222)
      return GraphElement.getStartRow(data, startRow);
   }

   private int getStartRow(String[] measures) {
      return Arrays.stream(measures).map(m -> measureRange.get(ElementVO.getBaseName(m)))
           .filter(r -> r != null)
           .mapToInt(r -> r[0])
           .min().orElse(0);
   }

   @Override
   public int getEndRow(DataSet data, String... measures) {
      int startRow = AbstractScaleRange.this.getStartRow(measures);
      int endRow = Arrays.stream(measures).map(m -> measureRange.get(ElementVO.getBaseName(m)))
         .filter(r -> r != null)
         .mapToInt(r -> r[1])
         .max().orElse(-1);
      return endRow < 0 ? data.getRowCount() : GraphElement.getEndRow(data, startRow, endRow, null);
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof AbstractScaleRange)) {
         return false;
      }

      AbstractScaleRange range = (AbstractScaleRange) obj;
      return abs == range.abs && measureRange.equals(range.measureRange);
   }

   private boolean abs;
   private final Map<String, int[]> measureRange = new HashMap<>();
}
