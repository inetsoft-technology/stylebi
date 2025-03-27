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
public class TableViewPropertyDialogModel implements Serializable {
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

   public TableAdvancedPaneModel getTableAdvancedPaneModel() {
      if(tableAdvancedPaneModel == null) {
         tableAdvancedPaneModel = new TableAdvancedPaneModel();
      }

      return tableAdvancedPaneModel;
   }

   public void setTableAdvancedPaneModel(TableAdvancedPaneModel tableAdvancedPaneModel) {
      this.tableAdvancedPaneModel = tableAdvancedPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   private String id;
   private TableViewGeneralPaneModel tableViewGeneralPaneModel;
   private TableAdvancedPaneModel tableAdvancedPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
