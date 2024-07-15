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
package inetsoft.web.viewsheet.event.chart;

/**
 * Class that encapsulates the parameters for resizing a plot x boundary spacing.
 *
 * @since 12.3
 */
public class VSChartPlotResizeEvent extends VSChartEvent {
   public void setSizeRatio(double ratio) {
      this.sizeRatio = ratio;
   }

   public double getSizeRatio() {
      return sizeRatio;
   }

   public void setHeightResized(boolean heightResized) {
      this.heightResized = heightResized;
   }

   public boolean isHeightResized() {
      return heightResized;
   }

   public boolean isReset() {
      return reset;
   }

   public void setReset(boolean reset) {
      this.reset = reset;
   }

   /**
    * width or height ratio for x or y resize
    */
   private double sizeRatio;

   /**
    * Whether this is an x or y resize
    */
   private boolean heightResized = false;

   /**
    * Whether to reset the scaling ignoring any other parameters
    */
   private boolean reset;
}
