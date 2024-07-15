/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.graph.model.dialog;

public class LegendScalePaneModel {
   public boolean isReverse() {
      return reverse;
   }

   public void setReverse(boolean reverse) {
      this.reverse = reverse;
   }

   public boolean isLogarithmic() {
      return logarithmic;
   }

   public void setLogarithmic(boolean logarithmic) {
      this.logarithmic = logarithmic;
   }

   public boolean isIncludeZero() {
      return includeZero;
   }

   public void setIncludeZero(boolean includeZero) {
      this.includeZero = includeZero;
   }

   public boolean isIncludeZeroVisible() {
      return includeZeroVisible;
   }

   public void setIncludeZeroVisible(boolean includeZeroVisible) {
      this.includeZeroVisible = includeZeroVisible;
   }

   public boolean isReverseVisible() {
      return reverseVisible;
   }

   public void setReverseVisible(boolean reverseVisible) {
      this.reverseVisible = reverseVisible;
   }

   private boolean reverse;
   private boolean logarithmic;
   private boolean includeZero;
   private boolean includeZeroVisible;
   private boolean reverseVisible;
}
