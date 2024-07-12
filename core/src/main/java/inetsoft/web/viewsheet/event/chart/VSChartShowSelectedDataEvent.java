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
 * Class that encapsulates the parameters for sorting a chart axis.
 *
 * @since 12.3
 */
public class VSChartShowSelectedDataEvent extends VSChartEvent {
   /**
    * Sets the selected for ShowDetails.
    *
    * @param selected the selected.
    */
   public void setSelected(String selected) {
      this.selected = selected;
   }

   /**
    * Gets the selected for ShowDetails.
    *
    * @return the selected.
    */
   public String getSelected() {
      return selected;
   }

   /**
    * Sets the Sort column, for "VSChartSortColumnEvent".
    *
    * @param sortColumnIndex the column to be sorted.
    */
   public void setSortColumnIndex(int sortColumnIndex) {
      this.sortColumnIndex = sortColumnIndex;
   }

   /**
    * Gets the Sort column, for "VSChartSortColumnEvent".
    *
    * @return the Sort Column Index.
    */
   public int getSortColumnIndex() {
      return sortColumnIndex;
   }

   /**
    * Sets the Sort column, for "VSChartSortColumnEvent".
    *
    * @param sortColumnDirection the direction of column sorting.
    */
   public void setSortColumnDirection(int sortColumnDirection) {
      this.sortColumnDirection = sortColumnDirection;
   }

   /**
    * Gets the Sort column, for "VSChartSortColumnEvent".
    *
    * @return the Sort Column Direction.
    */
   public int getSortColumnDirection() {
      return sortColumnDirection;
   }

   private String selected;
   private int sortColumnIndex;
   private int sortColumnDirection;
}
