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
package inetsoft.web.composer.model.vs;

public class SliderLabelPaneModel {
   public boolean isTick() {
      return tick;
   }

   public void setTick(boolean tick) {
      this.tick = tick;
   }

   public boolean isCurrentValue() {
      return currentValue;
   }

   public void setCurrentValue(boolean currentValue) {
      this.currentValue = currentValue;
   }

   public boolean isLabel() {
      return label;
   }

   public void setLabel(boolean label) {
      this.label = label;
   }

   public boolean isShowLabel() {
      return showLabel;
   }

   public void setShowLabel(boolean showLabel) {
      this.showLabel = showLabel;
   }

   public boolean isMinimum() {
      return minimum;
   }

   public void setMinimum(boolean minimum) {
      this.minimum = minimum;
   }

   public boolean isMaximum() {
      return maximum;
   }

   public void setMaximum(boolean maximum) {
      this.maximum = maximum;
   }

   private boolean tick;
   private boolean currentValue;
   private boolean showLabel;
   private boolean label;
   private boolean minimum;
   private boolean maximum;
}
