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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.NumericRangeVSAssembly;
import inetsoft.uql.viewsheet.internal.NumericRangeVSAssemblyInfo;

public abstract class VSNumericRangeModel<T extends NumericRangeVSAssembly>
   extends VSInputModel<T>
{
   protected VSNumericRangeModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      NumericRangeVSAssemblyInfo assemblyInfo =
        (NumericRangeVSAssemblyInfo) assembly.getVSAssemblyInfo();

      currLabel = assemblyInfo.getSelectedLabel();
      min = assemblyInfo.getMin();
      max = assemblyInfo.getMax();
      value = (Number) assemblyInfo.getSelectedObject();
      double doubleVal = value == null ? 0 : value.doubleValue();

      if(doubleVal > getMax()) {
         this.value = getMax();
      }
      else if(doubleVal < getMin()) {
         this.value = getMin();
      }

      increment = assemblyInfo.getIncrement();
   }

   public String getCurrentLabel() {
      return currLabel;
   }

   public double getMin() {
      return min;
   }

   public double getMax() {
      return max;
   }

   public Number getValue() {
      return value;
   }

   public double getIncrement() {
      return increment;
   }

   private String currLabel;
   private double min;
   private double max;
   private Number value;
   private double increment;
}
