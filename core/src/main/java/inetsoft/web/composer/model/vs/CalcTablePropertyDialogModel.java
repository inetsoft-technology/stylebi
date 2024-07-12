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
public class CalcTablePropertyDialogModel {
   public TableViewGeneralPaneModel getTableViewGeneralPaneModel() {
      if(tableViewGeneralPaneModel == null) {
         tableViewGeneralPaneModel = new TableViewGeneralPaneModel();
      }

      return tableViewGeneralPaneModel;

   }

   public void setTableViewGeneralPaneModel(
      TableViewGeneralPaneModel tableViewGeneralPaneModel)
   {
      this.tableViewGeneralPaneModel = tableViewGeneralPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   public CalcTableAdvancedPaneModel getCalcTableAdvancedPaneModel() {
      if(calcTableAdvancedPaneModel == null) {
         calcTableAdvancedPaneModel = new CalcTableAdvancedPaneModel();
      }

      return calcTableAdvancedPaneModel;
   }

   public void setCalcTableAdvancedPaneModel(
      CalcTableAdvancedPaneModel calcTableAdvancedPaneModel)
   {
      this.calcTableAdvancedPaneModel = calcTableAdvancedPaneModel;
   }

   TableViewGeneralPaneModel tableViewGeneralPaneModel;
   CalcTableAdvancedPaneModel calcTableAdvancedPaneModel;
   VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
