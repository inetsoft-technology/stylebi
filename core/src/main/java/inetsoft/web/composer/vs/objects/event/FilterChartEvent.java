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
package inetsoft.web.composer.vs.objects.event;

/**
 * Class that encapsulates the parameters for filtering a chart.
 *
 * @since 12.3
 */
public class FilterChartEvent extends VSObjectEvent {
   public String getColumnName() {
      return columnName;
   }

   public void setColumnName(String columnName) {
      this.columnName = columnName;
   }

   public boolean isLegend() {
      return legend;
   }

   public void setLegend(boolean legend) {
      this.legend = legend;
   }

   public int getTop() {
      return top;
   }

   public void setTop(int top) {
      this.top = top;
   }

   public int getLeft() {
      return left;
   }

   public void setLeft(int left) {
      this.left = left;
   }

   public boolean isDimension() {
      return dimension;
   }

   public void setDimension(boolean dimension) {
      this.dimension = dimension;
   }

   private String columnName;
   private boolean legend;
   private int top;
   private int left;
   private boolean dimension;
}