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
package inetsoft.web.viewsheet.event.chart;

import java.awt.*;

/**
 * Class that encapsulates the parameters for chart user actions.
 *
 * @since 12.3
 */
public class VSChartEvent {
   /**
    * Sets the chart name.
    *
    * @param chartName the chart name field.
    */
   public void setChartName(String chartName) {
      this.chartName = chartName;
   }

   /**
    * Gets the chart name.
    *
    * @return the chart name.
    */
   public String getChartName() {
      return chartName;
   }

   public Dimension getMaxSize() {
      return maxSize;
   }

   public void setMaxSize(Dimension maxSize) {
      this.maxSize = maxSize;
   }

   public int getViewportWidth() {
      return viewportWidth;
   }

   public void setViewportWidth(int viewportWidth) {
      this.viewportWidth = viewportWidth;
   }

   public int getViewportHeight() {
      return viewportHeight;
   }

   public void setViewportHeight(int viewportHeight) {
      this.viewportHeight = viewportHeight;
   }

   private String chartName;
   private Dimension maxSize;
   private int viewportWidth = 0;
   private int viewportHeight = 0;
}
