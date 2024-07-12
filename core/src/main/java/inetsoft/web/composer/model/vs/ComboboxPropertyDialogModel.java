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
 * Data transfer object that represents the {@link ComboboxPropertyDialogModel} for the
 * combobox property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComboboxPropertyDialogModel {
   public ComboboxGeneralPaneModel getComboboxGeneralPaneModel() {
      if(comboboxGeneralPaneModel == null) {
         comboboxGeneralPaneModel = new ComboboxGeneralPaneModel();
      }

      return comboboxGeneralPaneModel;
   }

   public void setComboboxGeneralPaneModel(
      ComboboxGeneralPaneModel comboboxGeneralPaneModel)
   {
      this.comboboxGeneralPaneModel = comboboxGeneralPaneModel;
   }

   public DataInputPaneModel getDataInputPaneModel() {
      if(dataInputPaneModel == null) {
         dataInputPaneModel = new DataInputPaneModel();
      }

      return dataInputPaneModel;
   }

   public void setDataInputPaneModel(
      DataInputPaneModel dataInputPaneModel)
   {
      this.dataInputPaneModel = dataInputPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(
      VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel)
   {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   private ComboboxGeneralPaneModel comboboxGeneralPaneModel;
   private DataInputPaneModel dataInputPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
