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

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartPropertyDialogModel {
   public ChartGeneralPaneModel getChartGeneralPaneModel() {
      if(chartGeneralPaneModel == null) {
         chartGeneralPaneModel = new ChartGeneralPaneModel();
      }

      return chartGeneralPaneModel;
   }

   public void setChartGeneralPaneModel(ChartGeneralPaneModel chartGeneralPaneModel) {
      this.chartGeneralPaneModel = chartGeneralPaneModel;
   }

   public ChartAdvancedPaneModel getChartAdvancedPaneModel() {
      return chartAdvancedPaneModel;
   }

   public void setChartAdvancedPaneModel(ChartAdvancedPaneModel chartAdvancedPaneModel) {
      this.chartAdvancedPaneModel = chartAdvancedPaneModel;
   }

   public ChartLinePaneModel getChartLinePaneModel() {
      return chartLinePaneModel;
   }

   public void setChartLinePaneModel(ChartLinePaneModel chartLinePaneModel) {
      this.chartLinePaneModel = chartLinePaneModel;
   }

   public HierarchyPropertyPaneModel getHierarchyPropertyPaneModel() {
      if(hierarchyPropertyPaneModel == null) {
         hierarchyPropertyPaneModel = new HierarchyPropertyPaneModel();
      }

      return hierarchyPropertyPaneModel;
   }

   public void setHierarchyPropertyPaneModel(
      HierarchyPropertyPaneModel hierarchyPropertyPaneModel)
   {
      this.hierarchyPropertyPaneModel = hierarchyPropertyPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   private ChartGeneralPaneModel chartGeneralPaneModel;
   private ChartAdvancedPaneModel chartAdvancedPaneModel;
   private ChartLinePaneModel chartLinePaneModel;
   private HierarchyPropertyPaneModel hierarchyPropertyPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
