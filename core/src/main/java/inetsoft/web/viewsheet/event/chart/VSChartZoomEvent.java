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
 * Class that encapsulates the parameters for zooming a chart.
 *
 * @since 12.3
 */
public class VSChartZoomEvent extends VSChartEvent {
   /**
    * Sets the axis / plot regions selected for zooming.
    *
    * @param selected the regions selected.
    */
   public void setSelected(String selected) {
      this.selected = selected;
   }

   /**
    * Gets the axis / plot regions selected for zooming.
    *
    * @return the regions selected.
    */
   public String getSelected() {
      return selected;
   }

   /**
    * Sets the rangeSelection flag for zooming.
    *
    * @param rangeSelection the flag which is always false.
    */
   public void setRangeSelection(boolean rangeSelection) {
      this.rangeSelection = rangeSelection;
   }

   /**
    * Gets the rangeSelection flag for zooming.
    *
    * @return the rangeSelection flag.
    */
   public boolean getRangeSelection() {
      return rangeSelection;
   }

   /**
    * Sets the exclude flag for zooming.
    *
    * @param exclude the flag which is always false (for ZOOM, it is set for EXCLUDE).
    */
   public void setExclude(boolean exclude) {
      this.exclude = exclude;
   }

   /**
    * Gets the exclude flag for zooming.
    *
    * @return the exclude flag.
    */
   public boolean getExclude() {
      return exclude;
   }

   private String selected;
   private boolean rangeSelection;
   private boolean exclude;
}
