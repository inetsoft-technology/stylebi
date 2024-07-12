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
package inetsoft.web.graph.model.dialog;

public class AxisLinePaneModel {
   public boolean isIgnoreNull() {
      return ignoreNull;
   }

   public void setIgnoreNull(boolean ignore) {
      this.ignoreNull = ignore;
   }

   public boolean isTruncate() {
      return truncate;
   }

   public void setTruncate(boolean truncate) {
      this.truncate = truncate;
   }

   public boolean isLogarithmicScale() {
      return logarithmicScale;
   }

   public void setLogarithmicScale(boolean logarithmicScale) {
      this.logarithmicScale = logarithmicScale;
   }

   public boolean isShowAxisLine() {
      return showAxisLine;
   }

   public void setShowAxisLine(boolean showAxisLine) {
      this.showAxisLine = showAxisLine;
   }

   public boolean isShowAxisLineEnabled() {
      return showAxisLineEnabled;
   }

   public void setShowAxisLineEnabled(boolean showAxisLineEnabled) {
      this.showAxisLineEnabled = showAxisLineEnabled;
   }

   public boolean isLineColorEnabled() {
      return lineColorEnabled;
   }

   public void setLineColorEnabled(boolean lineColorEnabled) {
      this.lineColorEnabled = lineColorEnabled;
   }

   public boolean isShowTicks() {
      return showTicks;
   }

   public void setShowTicks(boolean showTicks) {
      this.showTicks = showTicks;
   }

   public boolean isReverse() {
      return reverse;
   }

   public void setReverse(boolean reverse) {
      this.reverse = reverse;
   }

   public boolean isShared() {
      return shared;
   }

   public void setShared(boolean shared) {
      this.shared = shared;
   }

   public String getLineColor() {
      return lineColor;
   }

   public void setLineColor(String lineColor) {
      this.lineColor = lineColor;
   }

   public String getMinimum() {
      return minimum;
   }

   public void setMinimum(String minimum) {
      this.minimum = minimum;
   }

   public String getMaximum() {
      return maximum;
   }

   public void setMaximum(String maximum) {
      this.maximum = maximum;
   }

   public String getMinorIncrement() {
      return minorIncrement;
   }

   public void setMinorIncrement(String minorIncrement) {
      this.minorIncrement = minorIncrement;
   }

   public String getIncrement() {
      return increment;
   }

   public void setIncrement(String increment) {
      this.increment = increment;
   }

   public String getAxisType() {
      return axisType;
   }

   public void setAxisType(String axisType) {
      this.axisType = axisType;
   }

   public boolean isFakeScale() {
      return fakeScale;
   }

   public void setFakeScale(boolean fakeScale) {
      this.fakeScale = fakeScale;
   }

   private boolean ignoreNull;
   private boolean truncate;
   private boolean logarithmicScale;
   private boolean showAxisLine;
   private boolean showAxisLineEnabled;
   private boolean showTicks;
   private boolean lineColorEnabled;
   private boolean reverse;
   private boolean shared;
   private String lineColor;
   private String minimum;
   private String maximum;
   private String minorIncrement;
   private String increment;
   private String axisType;
   private boolean fakeScale;
}
