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

public class VSChartAxesVisibilityEvent extends VSChartEvent {
   /**
    * Get if the event it to hide an axis.\
    *
    * @return true if should hide axis.
    */
   public boolean isHide() {
      return hide;
   }

   /**
    * Gets if the axis is secondary
    *
    * @return true if the axis is secondary
    */
   public boolean isSecondary() {
      return secondary;
   }

   /**
    * Gets the column name of the axis to hide
    *
    * @return column name of axis or null to show all axis.
    */
   public String getColumnName() {
      return columnName;
   }

   private boolean hide;
   private String columnName;
   private boolean secondary;
}