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
 * Class that encapsulates the parameters for showing/hiding a chart's titles.
 *
 * @since 12.3
 */
public class VSChartTitlesVisibilityEvent extends VSChartEvent {
   /**
    * Gets if the event it to hide a title
    *
    * @return true if should hide.
    */
   public boolean isHide() {
      return hide;
   }

   /**
    * Get the title's type
    *
    * @return the title type.
    */
   public String getTitleType() {
      return titleType;
   }

   private boolean hide;
   private String titleType;
}
