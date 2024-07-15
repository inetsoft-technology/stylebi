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
 * Data transfer object that represents the {@link GaugePropertyDialogModel} for the
 * gauge property dialog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GaugePropertyDialogModel {
   public GaugeGeneralPaneModel getGaugeGeneralPaneModel() {
      if(gaugeGeneralPaneModel == null) {
         gaugeGeneralPaneModel = new GaugeGeneralPaneModel();
      }

      return gaugeGeneralPaneModel;
   }

   public void setGaugeGeneralPaneModel(GaugeGeneralPaneModel gaugeGeneralPaneModel) {
      this.gaugeGeneralPaneModel = gaugeGeneralPaneModel;
   }

   public DataOutputPaneModel getDataOutputPaneModel() {
      if(dataOutputPaneModel == null) {
         dataOutputPaneModel = new DataOutputPaneModel();
      }

      return dataOutputPaneModel;
   }

   public void setDataOutputPaneModel(DataOutputPaneModel dataOutputPaneModel) {
      this.dataOutputPaneModel = dataOutputPaneModel;
   }

   public GaugeAdvancedPaneModel getGaugeAdvancedPaneModel() {
      if(gaugeAdvancedPaneModel == null) {
         gaugeAdvancedPaneModel = new GaugeAdvancedPaneModel();
      }

      return gaugeAdvancedPaneModel;
   }

   public void setGaugeAdvancedPaneModel(GaugeAdvancedPaneModel gaugeAdvancedPaneModel) {
      this.gaugeAdvancedPaneModel = gaugeAdvancedPaneModel;
   }

   public VSAssemblyScriptPaneModel getVsAssemblyScriptPaneModel() {
      return vsAssemblyScriptPaneModel;
   }

   public void setVsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel) {
      this.vsAssemblyScriptPaneModel = vsAssemblyScriptPaneModel;
   }

   @Override
   public String toString() {
      return "GaugePropertyDialogModel{" +
         "gaugeGeneralPaneModel=" + gaugeGeneralPaneModel +
         ", dataOutputPaneModel=" + dataOutputPaneModel +
         ", gaugeAdvancedPaneModel=" + gaugeAdvancedPaneModel +
         ", vsAssemblyScriptPaneModel=" + vsAssemblyScriptPaneModel +
         '}';
   }

   private GaugeGeneralPaneModel gaugeGeneralPaneModel;
   private DataOutputPaneModel dataOutputPaneModel;
   private GaugeAdvancedPaneModel gaugeAdvancedPaneModel;
   private VSAssemblyScriptPaneModel vsAssemblyScriptPaneModel;
}
