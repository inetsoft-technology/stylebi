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
package inetsoft.web.composer.model.vs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CrosstabPropertyDialogModel implements Serializable {
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

   public CrosstabAdvancedPaneModel getCrosstabAdvancedPaneModel() {
      if(crosstabAdvancedPaneModel == null) {
         crosstabAdvancedPaneModel = new CrosstabAdvancedPaneModel();
      }

      return crosstabAdvancedPaneModel;
   }

   public void setCrosstabAdvancedPaneModel(
      CrosstabAdvancedPaneModel crosstabAdvancedPaneModel)
   {
      this.crosstabAdvancedPaneModel = crosstabAdvancedPaneModel;
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

   private TableViewGeneralPaneModel tableViewGeneralPaneModel;
   private CrosstabAdvancedPaneModel crosstabAdvancedPaneModel;
   private HierarchyPropertyPaneModel hierarchyPropertyPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
