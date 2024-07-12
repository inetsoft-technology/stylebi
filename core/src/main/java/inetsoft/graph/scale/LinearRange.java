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

import com.inetsoft.build.tern.TernClass;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;

/**
 * Calculate the range by taking the minimum and maximum of all data.
 */
@TernClass(url = "#cshid=LinearRange")
public class LinearRange extends AbstractScaleRange {
   @Override
   public double[] calculate(DataSet data, String[] cols, GraphtDataSelector selector) {
      double minValue = Double.MAX_VALUE;
      double maxValue = -Double.MAX_VALUE;
      boolean processed = false;

      for(int i = 0; i < data.getRowCount(); i++) {
         if(selector != null && !selector.accept(data, i, cols)) {
            continue;
         }

         for(int j = 0; j < cols.length; j++) {
            Object val = data.getData(cols[j], i);

            if(val == null || val.equals(Double.NaN)) {
               continue;
            }

            // use get value to map object properly
            double v = getValue(val);
            minValue = Math.min(v, minValue);
            maxValue = Math.max(v, maxValue);
            processed = true;
         }
      }

      // when no data, set min and max to be zero is more reasonable than
      // Double.MAX_VALUE, for it's hard for LinearScale to handle
      // Double.MAX_VALUE, so here we ignore it
      if(!processed) {
         return new double[] {0, 0};
      }

      minValue = Math.min(minValue, maxValue);
      return new double[] {minValue, maxValue};
   }

   private static final long serialVersionUID = 1L;
}
