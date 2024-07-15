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

/**
 * Data transfer object that represents the {@link SpinnerPropertyDialogModel} for the
 * textinput property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpinnerPropertyDialogModel {
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

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   public SpinnerGeneralPaneModel getSpinnerGeneralPaneModel() {
      if(spinnerGeneralPaneModel == null) {
         spinnerGeneralPaneModel = new SpinnerGeneralPaneModel();
      }

      return spinnerGeneralPaneModel;
   }

   public void setSpinnerGeneralPaneModel(
      SpinnerGeneralPaneModel spinnerGeneralPaneModel)
   {
      this.spinnerGeneralPaneModel = spinnerGeneralPaneModel;
   }

   private SpinnerGeneralPaneModel spinnerGeneralPaneModel;
   private DataInputPaneModel dataInputPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}