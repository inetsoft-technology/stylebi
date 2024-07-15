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
 * Class that encapsulates the parameters for sorting a chart axis.
 *
 * @since 12.3
 */
public class VSChartDrillEvent extends VSChartEvent {
   /**
    * Sets the Axis Type for chart drill.
    *
    * @param axisType the Axis Type.
    */
   public void setAxisType(String axisType) {
      this.axisType = axisType;
   }

   /**
    * Gets the Axis Type for chart drill.
    *
    * @return the Axis Type
    */
   public String getAxisType() {
      return axisType;
   }

   /**
    * Sets the Field, for chart drill.
    *
    * @param field the field to be drilled.
    */
   public void setField(String field) {
      this.field = field;
   }

   /**
    * Gets the Field, for chart drill.
    *
    * @return the field.
    */
   public String getField() {
      return field;
   }

   public boolean isDrillUp() {
      return drillUp;
   }

   public void setDrillUp(boolean drillUp) {
      this.drillUp = drillUp;
   }

   private String axisType;
   private String field;
   private boolean drillUp;
}
