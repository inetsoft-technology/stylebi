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

public class VSChartDrillActionEvent extends VSChartEvent {
   /**
    * Sets the axis / plot regions selected for drill.
    *
    * @param selected the regions selected.
    */
   public void setSelected(String selected) {
      this.selected = selected;
   }

   /**
    * Gets the axis / plot regions selected for drill.
    *
    * @return the regions selected.
    */
   public String getSelected() {
      return selected;
   }

   /**
    * Sets the rangeSelection flag for drill.
    *
    * @param rangeSelection the flag which is always false.
    */
   public void setRangeSelection(boolean rangeSelection) {
      this.rangeSelection = rangeSelection;
   }

   /**
    * Gets the rangeSelection flag for drill.
    *
    * @return the rangeSelection flag.
    */
   public boolean getRangeSelection() {
      return rangeSelection;
   }

   public boolean isDrillUp() {
      return drillUp;
   }

   public void setDrillUp(boolean drillUp) {
      this.drillUp = drillUp;
   }

   private String selected;
   private boolean rangeSelection;
   private boolean drillUp;
}
