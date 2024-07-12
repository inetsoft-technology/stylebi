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
package inetsoft.web.composer.model.vs;

public class RangeSliderSizePaneModel {
   public int getLength() {
      return length;
   }

   public void setLength(int length) {
      this.length = length;
   }

   public boolean isLogScale() {
      return logScale;
   }

   public void setLogScale(boolean logScale) {
      this.logScale = logScale;
   }

   public boolean isUpperInclusive() {
      return upperInclusive;
   }

   public void setUpperInclusive(boolean upperInclusive) {
      this.upperInclusive = upperInclusive;
   }

   public int getRangeType() {
      return rangeType;
   }

   public void setRangeType(int rangeType) {
      this.rangeType = rangeType;
   }

   public double getRangeSize() {
      return rangeSize;
   }

   public void setRangeSize(double rangeSize) {
      this.rangeSize = rangeSize;
   }

   public double getMaxRangeSize() {
      return maxRangeSize;
   }

   public void setMaxRangeSize(double maxRangeSize) {
      this.maxRangeSize = maxRangeSize;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public void setSubmitOnChange(boolean submitOnChange) {
      this.submitOnChange = submitOnChange;
   }

   private int length;
   private boolean logScale;
   private boolean upperInclusive;
   private int rangeType;
   private double rangeSize;
   private double maxRangeSize;
   private boolean submitOnChange;
}
