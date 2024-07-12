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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object that represents the {@link GaugePropertyDialogModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GaugeAdvancedPaneModel {
   public RangePaneModel getRangePaneModel() {
      if(rangePaneModel == null) {
         rangePaneModel = new RangePaneModel();
      }

      return rangePaneModel;
   }

   public void setRangePaneModel(
      RangePaneModel rangePaneModel)
   {
      this.rangePaneModel = rangePaneModel;
   }

   public boolean isShowValue() {
      return showValue;
   }

   public void setShowValue(boolean showValue) {
      this.showValue = showValue;
   }

   @Override
   public String toString() {
      return "GaugeAdvancedPaneModel{" +
         "rangePaneModel=" + rangePaneModel +
         '}';
   }

   private RangePaneModel rangePaneModel;
   private boolean showValue;
}