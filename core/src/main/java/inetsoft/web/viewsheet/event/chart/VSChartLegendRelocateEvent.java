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

import java.util.List;

/**
 * Class that encapsulates the parameters for relocating a legend to another
 *  position (north, south, east, west, center).
 *
 * @since 12.3
 */
public class VSChartLegendRelocateEvent extends VSChartEvent {
   /**
    * Sets the Legend Position for legend move.
    */
   public void setLegendPosition(int legendPosition) {
      this.legendPosition = legendPosition;
   }

   /**
    * Gets the Legend Position for legend move.
    *
    * @return the Legend Position.
    */
   public int getLegendPosition() {
      return legendPosition;
   }

   /**
    * Sets the Legend X for legend move.
    *
    * @param legendX the Legend X.
    */
   public void setLegendX(double legendX) {
      this.legendX = legendX;
   }

   /**
    * Gets the Legend X for legend move.
    *
    * @return the Legend X.
    */
   public double getLegendX() {
      return legendX;
   }

   /**
    * Sets the Legend Y for legend move.
    *
    * @param legendY the Legend Y.
    */
   public void setLegendY(double legendY) {
      this.legendY = legendY;
   }

   /**
    * Gets the Legend Y for legend move.
    *
    * @return the Legend Y.
    */
   public double getLegendY() {
      return legendY;
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

   private int legendPosition;
   private double legendX;
   private double legendY;
   private String legendType;
   private String field;
   private List<String> targetFields;
   private boolean nodeAesthetic;
}
