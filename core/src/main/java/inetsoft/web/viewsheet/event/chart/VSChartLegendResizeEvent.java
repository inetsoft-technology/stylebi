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

import java.util.List;

/**
 * Class that encapsulates the parameters for resizing the legend(s).
 *
 * @since 12.3
 */
public class VSChartLegendResizeEvent extends VSChartEvent {
   /**
    * Sets the Legend Width for legend resize.
    *
    * @param legendWidth the Legend Width.
    */
   public void setLegendWidth(double legendWidth) {
      this.legendWidth = legendWidth;
   }

   /**
    * Gets the Legend Width for legend resize.
    *
    * @return the Legend Width.
    */
   public double getLegendWidth() {
      return legendWidth;
   }

   /**
    * Sets the Legend Height for legend resize.
    *
    * @param legendHeight the Legend Height.
    */
   public void setLegendHeight(double legendHeight) {
      this.legendHeight = legendHeight;
   }

   /**
    * Gets the Legend Height for legend resize.
    *
    * @return the Legend Height.
    */
   public double getLegendHeight() {
      return legendHeight;
   }

   /**
    * Sets the Legend Type for legend move.
    *
    * @param legendType the Legend Type.
    */
   public void setLegendType(String legendType) {
      this.legendType = legendType;
   }

   /**
    * Gets the Legend Type for legend move.
    *
    * @return the Legend Type.
    */
   public String getLegendType() {
      return legendType;
   }

   /**
    * Set the legend field.
    */
   public void setField(String field) {
      this.field = field;
   }

   public String getField() {
      return field;
   }

   /**
    * Set the measure names this legend is for in multi-style chart.
    */
   public void setTargetFields(List<String> fields) {
      this.targetFields = fields;
   }

   public List<String> getTargetFields() {
      return targetFields;
   }

   public boolean isNodeAesthetic() {
      return nodeAesthetic;
   }

   public void setNodeAesthetic(boolean nodeAesthetic) {
      this.nodeAesthetic = nodeAesthetic;
   }

   private double legendWidth;
   private double legendHeight;
   private String legendType;
   private String field;
   private List<String> targetFields;
   private boolean nodeAesthetic;
}
