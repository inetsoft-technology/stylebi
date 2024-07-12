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
package inetsoft.web.viewsheet.event.chart;

/**
 * Class that encapsulates the parameters for resizing a chart axis.
 *
 * @since 12.3
 */
public class VSChartAxisResizeEvent extends VSChartEvent {
   /**
    * Get the axis type ('x' or 'y') used to determine if the contained axis size should
    * be applied to the height or width respectively
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Set the axis type ('x' or 'y') used to determine if the contained axis size should
    * be applied to the height or width respectively
    */
   public void setAxisType(String axisType) {
      this.axisType = axisType;
   }

   /**
    * Sets the axis field for axis x/y resize.
    *
    * @param axisField the axis field.
    */
   public void setAxisField(String axisField) {
      this.axisField = axisField;
   }

   /**
    * Gets the axis field for axis x/y resize.
    *
    * @return the axis field.
    */
   public String getAxisField() {
      return axisField;
   }

   /**
    * Sets the axis width for axis resize.
    *
    * @param axisSize the new axis size.
    */
   public void setAxisSize(int axisSize) {
      this.axisSize = axisSize;
   }

   /**
    * Gets the axis width for axis resize.
    *
    * @return the new axis size.
    */
   public int getAxisSize() {
      return axisSize;
   }

   private String axisType;
   private String axisField;
   private int axisSize;
}
