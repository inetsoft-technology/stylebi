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
package inetsoft.web.binding.command;

import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartGeoRefModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

public class OpenEditGeographicCommand implements ViewsheetCommand {

   public OpenEditGeographicCommand(ChartBindingModel bindingModel,
                                    ChartGeoRefModel chartGeoRefModel)
   {
      this.bindingModel = bindingModel;
      this.chartGeoRefModel = chartGeoRefModel;
   }

   public OpenEditGeographicCommand(ChartBindingModel bindingModel, String measureName) {
      this.bindingModel = bindingModel;
      this.measureName = measureName;
   }

   public BindingModel getBindingModel() {
      return bindingModel;
   }

   public void setBindingModel(ChartBindingModel bindingModel) {
      this.bindingModel = bindingModel;
   }

   public ChartGeoRefModel getChartGeoRefModel() {
      return chartGeoRefModel;
   }

   public void setChartGeoRefModel(ChartGeoRefModel chartGeoRefModel) {
      this.chartGeoRefModel = chartGeoRefModel;
   }

   public String getMeasureName() {
      return measureName;
   }

   public void setMeasureName(String measureName) {
      this.measureName = measureName;
   }

   private ChartBindingModel bindingModel;
   private ChartGeoRefModel chartGeoRefModel;
   private String measureName;
}
