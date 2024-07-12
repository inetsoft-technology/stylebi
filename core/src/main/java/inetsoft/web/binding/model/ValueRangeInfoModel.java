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
package inetsoft.web.binding.model;

import inetsoft.uql.asset.InclusiveType;
import inetsoft.uql.asset.ValueRangeInfo;

public class ValueRangeInfoModel {

   public ValueRangeInfoModel() {
   }

   public ValueRangeInfoModel(ValueRangeInfo info) {
      values = info.getValues();
      labels = info.getLabels();
      showingBottomValue = info.isShowBottomValue();
      showingTopValue = info.isShowTopValue();
      inclusiveType = info.getInclusiveType();
   }

   public ValueRangeInfo convertFromModel() {
      ValueRangeInfo info = new ValueRangeInfo();
      info.setValues(values);
      info.setLabels(labels);
      info.setShowBottomValue(showingBottomValue);
      info.setShowTopValue(showingTopValue);
      info.setInclusiveType(inclusiveType);

      return info;
   }

   public double[] getValues() {
      if(values == null) {
         values = new double[0];
      }

      return values;
   }

   public void setValues(double[] values) {
      this.values = values;
   }

   public String[] getLabels() {
      if(labels == null) {
         labels = new String[0];
      }

      return labels;
   }

   public void setLabels(String[] labels) {
      this.labels = labels;
   }

   public boolean getShowingBottomValue() {
      return showingBottomValue;
   }

   public void setShowingBottomValue(boolean showingBottomValue) {
      this.showingBottomValue = showingBottomValue;
   }

   public boolean getShowingTopValue() {
      return showingTopValue;
   }

   public void setShowingTopValue(boolean showingTopValue) {
      this.showingTopValue = showingTopValue;
   }

   public InclusiveType getInclusiveType() {
      return inclusiveType;
   }

   public void setInclusiveType(InclusiveType inclusiveType) {
      this.inclusiveType = inclusiveType;
   }

   private double[] values;
   private String[] labels;
   private boolean showingBottomValue;
   private boolean showingTopValue;
   private InclusiveType inclusiveType;
}
